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
from app.models.domain.user import FullUser

# 解决bcrypt版本兼容性问题

# 降级到使用sha256_crypt作为替代方案
pwd_context = CryptContext(schemes=["sha256_crypt"], deprecated="auto")

oauth2_scheme = OAuth2PasswordBearer(tokenUrl="token/get")


def verify_password(plain_password: str, hashed_password: str) -> bool:
    """
    验证明文密码是否与哈希值匹配

    @param plain_password: 明文密码
    @param hashed_password: 密码哈希值
    @return bool: 密码匹配返回True,否则返回False
    """
    return pwd_context.verify(plain_password, hashed_password)


def get_password_hash(password: str) -> str:
    """
    将明文密码转换为哈希值

    @param password: 明文密码
    @return str: 密码哈希值
    """
    return pwd_context.hash(password)


def create_access_token(data: dict[str, Any], expires_delta: int | None = None) -> str:
    """
    创建JWT访问令牌

    @param data: 要编码到令牌中的载荷数据字典
    @param expires_delta: 令牌有效期(秒),如果为None则使用默认配置
    @return str: JWT令牌字符串
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
    从JWT令牌中获取当前用户信息

    @param token: JWT访问令牌字符串
    @return FullUser: 完整用户对象,包含权限信息
    @raise AppError: 当令牌无效或过期时抛出401未授权错误
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
        credentials_exception.message = f"Invalid token: {e}"
        raise credentials_exception from e

    # 这里应该从数据库获取用户
    # 简化处理,直接返回用户名
    return FullUser(**user)


def create_refresh_token() -> str:
    """
    生成安全的随机刷新令牌

    @return str: 512位随机字符串作为刷新令牌
    """
    return token_urlsafe(64)  # 512-bit 随机字符串
