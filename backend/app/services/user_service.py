from typing import Any

from fastapi import status
from sqlalchemy.exc import IntegrityError
from sqlmodel import select
from sqlmodel.ext.asyncio.session import AsyncSession

from app.core.error_handler import AppError
from app.core.security import get_password_hash, verify_password
from app.enum.enum import RoleEnum
from app.models.sql.user import FullUser, User
from app.services.captcha_service import CaptchaService
from app.services.right_service import RightService


class UserService:
    """
    用户服务类,处理用户相关的业务逻辑
    """

    @staticmethod
    async def create_user(
        db: AsyncSession, username: str, email: str, password: str, avatar: str = ""
    ):
        """
        创建新用户

        Args:
            db: 数据库会话依赖
            username: 用户名
            email: 邮箱
            password: 明文密码
            avatar: 头像URL

        Returns:
            User: 创建的用户对象,包含数据库自动生成的ID

        Raises:
            CustomException: 当用户名或邮箱已存在时抛出
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
            await RightService.add_user_role(db_user.id, RoleEnum.USER.value)
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

        Args:
            db: 数据库会话依赖
            user_email:  用户邮箱
            password: <PASSWORD>

        Returns:
            User: 如果凭据有效,则返回用户对象

        Raises:
            CustomException: 当用户名或密码无效时抛出
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

        Args:
            db: 数据库会话依赖
            username: 用户名
            old_password: <PASSWORD>
            new_password: <PASSWORD>

        Returns:
            bool: 如果密码更新成功,则返回True,否则返回False

        Raises:
            CustomException: 当旧密码无效时抛出
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

        Args:
            db: 数据库会话依赖
            user_id: 用户ID

        Returns:
            FullUser
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

        Args:
            db: 数据库会话依赖
            user_email: 用户邮箱

        Returns:
            User: 用户对象
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

        Args:
            user: 用户对象

        Returns:
            - id: 用户ID
            - username: 用户名
            - email: 邮箱
            - avatar: 头像URL
            - is_active: 是否激活
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
        忘记密码

        Args:
            db: 数据库会话依赖
            user_account: 用户账号
            new_password: <PASSWORD>
            verify_code: 验证码

        Returns:
            bool: 如果密码重置成功,则返回True,否则返回False

        Raises:
            CustomException: 当用户不存在时抛出
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
