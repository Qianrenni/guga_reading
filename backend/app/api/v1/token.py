import asyncio
import json
from re import match
from typing import Annotated, Any

from fastapi import APIRouter, BackgroundTasks, Body, Depends, Header, Query, status
from pydantic import BaseModel, Field
from sqlmodel.ext.asyncio.session import AsyncSession
from starlette.responses import HTMLResponse

from app.core.config import SETTING
from app.core.database import get_session
from app.core.error_handler import AppError
from app.core.security import (
    create_access_token,
    create_refresh_token,
    get_current_user,
)
from app.models.response_model import ResponseModel
from app.models.sql.user import FullUser
from app.services.cache_service import cache_delete, cache_get, cache_set
from app.services.captcha_service import CaptchaService
from app.services.email_service import email_sender
from app.services.user_service import UserService

token_router = APIRouter(prefix="/token", tags=["token"])


class TokenData(BaseModel):
    access_token: str
    refresh_token: str
    token_type: str = "Bearer"
    user: dict[str, Any]


class Token(ResponseModel):
    """
    令牌模型
    - data
    - - access_token (str): 访问令牌
    - - token_type (str): 令牌类型
    """

    data: TokenData | None = None


@token_router.post("/get", response_model=Token)
async def login_for_access_token(
    username: Annotated[
        str,
        Body(embed=True),
        Field(
            pattern=r"^[a-zA-Z0-9]+@[a-zA-Z0-9]+\.[a-zA-Z]{2,}$",
            description="Email address of the user",
        ),
    ],
    password: Annotated[str, Body(embed=True)],
    database: Annotated[AsyncSession, Depends(get_session)],
    captcha: Annotated[str, Body(embed=True)],
    x_captcha_id: Annotated[str, Header(name="X-Captcha-Id")],
) -> ResponseModel:
    """
    处理用户登录请求并生成访问令牌

    该函数接收用户名和密码,验证用户身份后生成JWT访问令牌。
    如果认证失败,将抛出HTTP 401未授权异常。

    - param form_data: OAuth2密码请求表单数据,包含用户名(邮箱)和密码
    - return: 包含访问令牌和令牌类型的Token对象
    - raises HTTPException: 当用户名或密码不正确时抛出401未授权异常
    """
    is_valid_captcha = await CaptchaService.verify_captcha(
        captcha_id=x_captcha_id, captcha_text=captcha
    )
    if not is_valid_captcha:
        raise AppError(status_code=status.HTTP_400_BAD_REQUEST, message="验证码错误")
    user_dict = {"user_email": username, "password": password}
    user = await UserService.authenticate_user(db=database, **user_dict)
    access_token = create_access_token(
        data={"sub": json.dumps(UserService.transform_user_to_payload(user))}
    )
    refresh_token = create_refresh_token()
    is_cached = await cache_set(
        value=user.id,
        key_prefix=f"{refresh_token}",
        expire=SETTING.REFRESH_TOKEN_EXPIRE,
    )
    if not is_cached:
        raise AppError(message="服务器内部错误,请稍后重试")
    return Token(
        data=TokenData(
            access_token=access_token,
            refresh_token=refresh_token,
            user=UserService.transform_user_to_payload(user),
        )
    )


@token_router.post("/refresh", response_model=Token)
async def refresh_access_token(
    authorization: Annotated[str, Header(name="Authorization")],
    database: Annotated[AsyncSession, Depends(get_session)],
) -> Token:
    """
    刷新访问令牌

    该函数接收一个刷新访问令牌,并使用它来生成一个新的访问令牌。
    - param token: 访问令牌
    - return: 刷新后的访问令牌
    """
    error = AppError(
        message="请先登录",
        status_code=status.HTTP_400_BAD_REQUEST,
        headers={"WWW-Authenticate": "Bearer"},
    )
    if not authorization or not authorization.startswith("Bearer "):
        raise error
    refresh_token = authorization.split(" ")[1]
    user_id = await cache_get(key_prefix=f"{refresh_token}")
    if not user_id:
        raise error
    else:
        _, user = await asyncio.gather(
            cache_delete(key_prefix=f"{refresh_token}"),
            UserService.get_user_by_id(user_id=user_id, db=database),
        )
        access_token = create_access_token(
            data={"sub": json.dumps(UserService.transform_user_to_payload(user))}
        )
        refresh_token = create_refresh_token()
        is_cached = await cache_set(
            value=user.id,
            key_prefix=f"{refresh_token}",
            expire=SETTING.REFRESH_TOKEN_EXPIRE,
        )
        if not is_cached:
            raise AppError(message="服务器内部错误,请稍后重试")
        return Token(
            data=TokenData(
                access_token=access_token,
                refresh_token=refresh_token,
                user=UserService.transform_user_to_payload(user),
            )
        )


def is_valid_email(email: str) -> bool:
    pattern = r"^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$"
    return match(pattern, email) is not None


def is_allowed_domain(email: str) -> bool:
    allowed_domains = {"gmail.com", "qq.com", "163.com", "foxmail.com", "outlook.com"}
    domain = email.split("@")[-1].lower()
    return domain in allowed_domains


@token_router.post("/verify_email", status_code=status.HTTP_201_CREATED)
async def verify_email(
    email: Annotated[str, Body(embed=True)], background_tasks: BackgroundTasks
):
    if not is_valid_email(email) or not is_allowed_domain(email):
        # 仍然返回 204,但不发邮件
        return ResponseModel()
    # 用refresh_token的方法代替
    token = create_refresh_token()

    # 缓存 token -> email,5分钟过期
    await cache_set(
        key_prefix=f"email_verify:{token}",
        value=email,
        expire=SETTING.EMAIL_VERIFY_EXPIRE,
    )

    verify_url = f"{SETTING.SERVER_URL}/token/verify_email?token={token}"

    background_tasks.add_task(
        email_sender.send_email,
        to_emails=[email],
        subject="在线阅读系统注册邮箱验证",
        body=f'点击以下链接完成邮箱验证:<br><a href="{verify_url}">注册邮箱验证</a>',
        is_html=True,
    )

    return ResponseModel()


@token_router.get("/verify_email")  # 注意:用 GET,用户点击链接
async def verify_email_callback(token: Annotated[str, Query()]):
    # 从缓存中获取邮箱
    email = await cache_get(key_prefix=f"email_verify:{token}")
    if not email:
        raise AppError(status_code=400, message="无效或已过期的验证链接")

    # 清除 token(防止重复使用)
    await cache_delete(key_prefix=f"email_verify:{token}")
    # 将邮箱标记为“已验证”,有效期 5 分钟(给用户留注册时间)
    await cache_set(
        key_prefix=f"email_verified:{email}",  # 注意:key 包含邮箱
        value=True,  # 值可以是任意非空(如 "true")
        expire=SETTING.EMAIL_VERIFY_EXPIRE,  # 必须 ≤ 验证 token 的有效期
    )
    # 返回友好页面
    html_content = """
    <html>
        <body>
            <h2>邮箱验证成功/h2>
            <p>您现在可以关闭此页面并返回应用,请在 5 分钟内完成注册</p>
        </body>
    </html>
    """
    return HTMLResponse(content=html_content)


@token_router.get("/auth/me", response_model=ResponseModel)
async def get_me(
    Authorization: Annotated[str, Header(name="Authorization")],  # noqa: ARG001, N803
    current_user: Annotated[FullUser, Depends(get_current_user)],
):
    """
    获取当前用户信息
    :param authorization:  访问令牌
    :return:  用户信息
    """

    return ResponseModel(
        data={"user": UserService.transform_user_to_payload(current_user)}
    )
