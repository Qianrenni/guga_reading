import json
from secrets import token_urlsafe
from time import time
from typing import Annotated, Any

import jwt
from fastapi import Depends, status
from fastapi.security import OAuth2PasswordBearer
from jwt.exceptions import InvalidTokenError
from passlib.context import CryptContext

from app.core.config import SETTING
from app.core.error_handler import AppError
from app.middleware.logging import logger
from app.models.sql.user import FullUser

# 解决bcrypt版本兼容性问题

# 降级到使用sha256_crypt作为替代方案
pwd_context = CryptContext(schemes=["sha256_crypt"], deprecated="auto")

oauth2_scheme = OAuth2PasswordBearer(tokenUrl="token/get")


def verify_password(plain_password: str, hashed_password: str):
    """
    验证密码是否正确
    Args:
        plain_password (str): 明文密码
        hashed_password (str): 密码哈希值
    Returns:
        bool: 如果密码正确,则返回True,否则返回False
    """
    return pwd_context.verify(plain_password, hashed_password)


def get_password_hash(password: str):
    """
    获取密码哈希值
    Args:
        password (str): 密码
    Returns:
        str: 密码哈希值
    """
    return pwd_context.hash(password)


def create_access_token(data: dict[str, Any], expires_delta: int | None = None):
    """
    创建访问令牌
    Args:
        data (dict[str,Any]): 令牌数据
        expires_delta (timedelta | None, optional): 令牌过期时间. Defaults to None.
    Returns:
        str: 令牌字符串
    """
    to_encode = data.copy()
    if expires_delta:
        # 时间戳
        expire = time() + expires_delta
    else:
        expire = time() + SETTING.ACCESS_TOKEN_EXPIRE
    to_encode.update({"exp": expire})
    encoded_jwt = jwt.encode(to_encode, SETTING.SECRET_KEY, algorithm=SETTING.ALGORITHM)
    return encoded_jwt


async def get_current_user(token: Annotated[str, Depends(oauth2_scheme)]) -> FullUser:
    """
    获取当前用户
    该函数接收一个访问令牌,并使用它来验证用户身份。
    如果访问令牌无效或已过期,将抛出HTTP 401未授权异常。
    - param token: 访问令牌
    - return: 当前用户
    - raises HTTPException: 当访问令牌无效或已过期时抛出401未授权异常
    """
    credentials_exception = AppError(
        status_code=status.HTTP_401_UNAUTHORIZED,
        message="Could not validate credentials",
        headers={"WWW-Authenticate": "Bearer"},
    )
    try:
        payload = jwt.decode(token, SETTING.SECRET_KEY, algorithms=[SETTING.ALGORITHM])
        user = json.loads(payload.get("sub"))
        expire = payload.get("exp")
        if not user:
            raise credentials_exception
        if not expire or expire < time():
            credentials_exception.message = "Token expired"
            raise credentials_exception
    except InvalidTokenError as e:
        logger.error(f"Invalid token: {e}")
        credentials_exception.message = f"Invalid token: {e}"
        raise credentials_exception from e

    # 这里应该从数据库获取用户
    # 简化处理,直接返回用户名
    return FullUser(**user)


def create_refresh_token() -> str:
    """生成一个安全的随机 refresh token"""
    return token_urlsafe(64)  # 512-bit 随机字符串
