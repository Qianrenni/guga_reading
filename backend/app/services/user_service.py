from typing import Any

from fastapi import status
from sqlalchemy.exc import IntegrityError
from sqlmodel import func, select
from sqlmodel.ext.asyncio.session import AsyncSession

from app.core.error_handler import AppError
from app.core.security import get_password_hash, verify_password
from app.enum.enum import RoleEnum
from app.models.database.user import User
from app.models.domain.user import FullUser
from app.services.cache_service import cache
from app.services.captcha_service import CaptchaService
from app.services.right_service import RightService


class UserService:
    """
    用户服务类,处理用户相关的业务逻辑
    """

    @staticmethod
    @cache(exclude_kwargs=["db"])
    async def get_user_count(
        db: AsyncSession,
    ) -> int:
        """
        获取用户总数

        @param db: 数据库会话对象
        @return int: 用户总数,如果无用户则返回0
        @raise AppError: 数据库查询失败时抛出
        """
        statement = select(func.count()).select_from(User)
        result = await db.exec(statement)
        return result.one_or_none() or 0

    @staticmethod
    async def create_user(
        db: AsyncSession, username: str, email: str, password: str, avatar: str = ""
    ):
        """
        创建新用户

        @param db: 数据库会话对象
        @param username: 用户名,3-20个字符,只能包含字母、数字、下划线和中文
        @param email: 邮箱地址,需符合标准邮箱格式
        @param password: 明文密码,至少6个字符
        @param avatar: 头像URL,可选,默认为空字符串
        @return None: 无返回值,创建成功后用户对象会自动刷新
        @raise AppError: 当用户名或邮箱已存在时抛出409冲突错误
        @raise AppError: 当用户创建失败时抛出500内部服务器错误
        """
        # 检查用户名是否已存在
        statement = select(User).where(User.username == username)
        result = await db.exec(statement)
        if result.first():
            raise AppError(status_code=status.HTTP_409_CONFLICT, message="用户名已存在")

        # 检查邮箱是否已存在
        statement = select(User).where(User.email == email)
        result = await db.exec(statement)
        if result.first():
            raise AppError(status_code=status.HTTP_409_CONFLICT, message="邮箱已被注册")

        # 创建新用户
        hashed_password = get_password_hash(password)
        db_user = User(
            username=username, password=hashed_password, email=email, avatar=avatar
        )

        try:
            db.add(db_user)
            await db.flush()
            await db.refresh(db_user)
            await RightService.add_user_role(db_user.id, RoleEnum.USER, database=db)
            await db.commit()
        except IntegrityError as e:
            await db.rollback()
            raise AppError(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                message="用户创建失败,可能用户名或邮箱已存在",
            ) from e

    @staticmethod
    async def authenticate_user(
        db: AsyncSession, user_email: str, password: str
    ) -> FullUser:
        """
        验证用户凭据

        @param db: 数据库会话对象
        @param user_email: 用户邮箱地址
        @param password: 明文密码
        @return FullUser: 验证成功后返回完整用户对象,包含权限信息
        @raise AppError: 当用户不存在时抛出400错误
        @raise AppError: 当用户未激活时抛出400错误
        @raise AppError: 当用户无任何权限时抛出400错误
        @raise AppError: 当密码错误时抛出400错误
        """
        statement = select(User).where(User.email == user_email)
        result = await db.exec(statement)
        user = result.first()
        if not user:
            raise AppError(
                status_code=status.HTTP_400_BAD_REQUEST, message="用户名无效"
            )
        if not user.is_active:
            raise AppError(
                status_code=status.HTTP_400_BAD_REQUEST, message="用户未激活"
            )
        if verify_password(password, user.password):
            user_role = await RightService.get_user_role(user.id, db)
            if len(user_role) == 0:
                raise AppError(
                    status_code=status.HTTP_400_BAD_REQUEST, message="用户无任何权限"
                )
            right_code_list = RightService.get_merged_permission_bitmap(
                [ur.role_id for ur in user_role]
            )
            return FullUser(**user.__dict__, right=right_code_list)
        else:
            raise AppError(status_code=status.HTTP_400_BAD_REQUEST, message="密码无效")

    @staticmethod
    async def update_password(
        db: AsyncSession, user_email: str, old_password: str, new_password: str
    ) -> bool:
        """
        更新用户密码

        @param db: 数据库会话对象
        @param user_email: 用户邮箱地址
        @param old_password: 旧密码,用于验证用户身份
        @param new_password: 新密码,将替换旧密码
        @return bool: 密码更新成功返回True
        @raise AppError: 当用户不存在时抛出404错误
        @raise AppError: 当旧密码错误时抛出400错误
        @raise ValueError: 当数据库提交失败时抛出
        """
        statement = select(User).where(User.email == user_email)
        user = (await db.exec(statement)).first()

        if not user:
            raise AppError(status_code=status.HTTP_404_NOT_FOUND, message="账号不存在")
        if not verify_password(old_password, user.password):
            raise AppError(
                status_code=status.HTTP_400_BAD_REQUEST, message="旧密码错误"
            )

        user.password = get_password_hash(new_password)

        try:
            await db.commit()
            await db.refresh(user)
            return True
        except Exception as e:
            await db.rollback()
            raise ValueError(f"密码更新失败: {e}") from e

    @staticmethod
    async def get_user_by_id(db: AsyncSession, user_id: int) -> FullUser:
        """
        根据用户ID获取用户信息

        @param db: 数据库会话对象
        @param user_id: 用户唯一标识ID
        @return FullUser: 完整用户对象,包含权限信息
        @raise AppError: 当用户不存在时抛出404错误
        """
        statement = select(User).where(User.id == user_id)
        result = await db.exec(statement)
        user = result.first()
        if user is None:
            raise AppError(status_code=status.HTTP_404_NOT_FOUND, message="用户不存在")
        else:
            user_role = await RightService.get_user_role(user.id, db)
            right_code_list = RightService.get_merged_permission_bitmap(
                [ur.role_id for ur in user_role]
            )
            return FullUser(**user.__dict__, right=right_code_list)

    @staticmethod
    async def get_user_by_email(
        db: AsyncSession,
        user_email: str,
    ) -> User:
        """
        根据用户邮箱获取用户信息

        @param db: 数据库会话对象
        @param user_email: 用户邮箱地址
        @return User: 用户对象
        @raise AppError: 当用户不存在时抛出404错误
        """
        statement = select(User).where(User.email == user_email)
        result = await db.exec(statement)
        user = result.first()
        if user is None:
            raise AppError(status_code=status.HTTP_404_NOT_FOUND, message="用户不存在")
        else:
            return user

    @staticmethod
    def transform_user_to_payload(user: FullUser) -> dict[str, Any]:
        """
        将用户对象转换为响应数据

        @param user: 完整用户对象
        @return dict[str, Any]: 包含用户基本信息的字典,包括id、username、email、avatar、is_active、right
        """
        return {
            "id": user.id,
            "username": user.username,
            "email": user.email,
            "avatar": user.avatar,
            "is_active": user.is_active,
            "right": user.right,
        }

    @staticmethod
    async def forgot_password(
        db: AsyncSession, user_account: str, new_password: str, verify_code: str
    ) -> bool:
        """
        忘记密码 - 重置用户密码

        @param db: 数据库会话对象
        @param user_account: 用户账号(邮箱地址)
        @param new_password: 新密码,将替换旧密码
        @param verify_code: 验证码,用于验证用户身份
        @return bool: 密码重置成功返回True
        @raise AppError: 当验证码错误时抛出400错误
        @raise AppError: 当用户不存在时抛出404错误
        """
        is_verify_code_valid = await CaptchaService.verify_code(
            key_prefix=f"forgot_password:{user_account}", answer=verify_code
        )
        if not is_verify_code_valid:
            raise AppError(
                status_code=status.HTTP_400_BAD_REQUEST, message="验证码错误"
            )
        user = await UserService.get_user_by_email(db, user_account)
        user.password = get_password_hash(new_password)
        await db.commit()
        return True
