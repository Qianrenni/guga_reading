import re
from datetime import datetime

from fastapi import status
from pydantic import BaseModel, Field, field_validator

from app.core.error_handler import AppError


class FullUser(BaseModel):
    id: int
    username: str
    password: str | None = None
    email: str
    is_active: bool
    avatar: str
    created_at: datetime | None = None
    # 权限
    right: list[int]


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
