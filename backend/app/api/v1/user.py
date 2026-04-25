import re
from typing import Annotated

from fastapi import APIRouter, BackgroundTasks, Body, Depends, Header, Query, status
from pydantic import BaseModel, Field, field_validator
from sqlmodel.ext.asyncio.session import AsyncSession

from app.core.database import get_session
from app.core.error_handler import AppError
from app.services.cache_service import cache_delete, cache_get
from app.services.captcha_service import CaptchaService
from app.services.email_service import email_sender
from app.services.user_service import UserService

user_router = APIRouter(prefix="/user", tags=["user"])


class UserRegister(BaseModel):
    username: str
    password: str
    email: str = Field(..., pattern=r"^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$")
    avatar: str = ""

    @field_validator("username")
    @classmethod
    def validate_username(cls, username: str):
        if len(username) < 3:
            raise AppError(
                status_code=status.HTTP_400_BAD_REQUEST,
                message="用户名长度至少为3个字符",
            )
        if len(username) > 20:
            raise AppError(
                status_code=status.HTTP_400_BAD_REQUEST,
                message="用户名长度不能超过20个字符",
            )
        if not re.match(r"^[a-zA-Z0-9_\u4e00-\u9fa5]+$", username):
            raise AppError(
                status_code=status.HTTP_400_BAD_REQUEST,
                message="用户名只能包含字母、数字、下划线和中文字符",
            )
        return username

    @field_validator("password")
    @classmethod
    def validate_password(cls, password: str):
        if len(password) < 6:
            raise AppError(
                status_code=status.HTTP_400_BAD_REQUEST, message="密码长度至少为6个字符"
            )
        return password


@user_router.post("/register", status_code=status.HTTP_201_CREATED)
async def register(
    user: Annotated[UserRegister, Body()],
    database: Annotated[AsyncSession, Depends(get_session)],
    captcha: Annotated[str, Body(embed=True)],
    x_captcha_id: Annotated[str, Header(name="X-Captcha-Id")],
):
    """
    用户注册接口

    - **username**: 用户名,3-20个字符,只能包含字母、数字、下划线和中文
    - **password**: 密码,至少6个字符
    - **email**: 邮箱地址,需符合标准邮箱格式
    - **avatar**: 头像URL,可选

    返回创建的用户信息
    """
    key_prefix = f"email_verified:{user.email}"
    if not await CaptchaService.verify_captcha(
        captcha_id=x_captcha_id, captcha_text=captcha
    ):
        raise AppError(status_code=status.HTTP_400_BAD_REQUEST, message="验证码错误")
    is_verify_email = await cache_get(key_prefix=key_prefix)
    if not is_verify_email:
        raise AppError(status_code=status.HTTP_400_BAD_REQUEST, message="邮箱未验证")
    await UserService.create_user(
        db=database,
        username=user.username,
        email=user.email,
        password=user.password,
        avatar=user.avatar,
    )
    await cache_delete(key_prefix=key_prefix)
    return


class UserPasswordUpdate(BaseModel):
    """
    用户密码更新模型
    - **username**: 邮箱地址
    - **password**: 当前密码
    - **new_password**: 新密码
    """

    username: str
    old_password: str
    new_password: str


@user_router.patch("/update-password", status_code=status.HTTP_204_NO_CONTENT)
async def update_user(
    user: Annotated[UserPasswordUpdate, Body()],
    database: Annotated[AsyncSession, Depends(get_session)],
):
    """
    更新用户密码

    - **username**: 用户名
    - **password**: 当前密码
    - **new_password**: 新密码

    返回更新后的用户信息
    """
    result = await UserService.update_password(
        db=database,
        user_email=user.username,
        old_password=user.old_password,
        new_password=user.new_password,
    )
    if result:
        return


@user_router.get("/forgot-password", status_code=status.HTTP_204_NO_CONTENT)
async def get_forgot_password(
    user_account: Annotated[str, Query()],
    background_tasks: BackgroundTasks,
):
    """
    忘记密码验证邮箱,获取验证码
    """
    code = await CaptchaService.get_verify_code(
        key_prefix=f"forgot_password:{user_account}"
    )
    background_tasks.add_task(
        email_sender.send_email,
        to_emails=[user_account],
        subject="忘记密码验证码",
        body=f"您的验证码为:{code},请勿将验证码告知他人。",
        is_html=False,
    )
    return


@user_router.patch("/forgot-password", status_code=status.HTTP_204_NO_CONTENT)
async def forgot_password(
    user_account: Annotated[str, Body(min_length=3, max_length=20)],
    verify_code: Annotated[str, Body(max_length=6, min_length=6)],
    password: Annotated[str, Body(min_length=6)],
    database: Annotated[AsyncSession, Depends(get_session)],
):
    """
    忘记密码接口
    :param: user_account: 用户账号
    :param: verify_code: 验证码
    :param: password: 新密码
    """
    result = await UserService.forgot_password(
        db=database,
        user_account=user_account,
        verify_code=verify_code,
        new_password=password,
    )
    if result:
        return
