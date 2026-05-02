from datetime import datetime

from sqlmodel import (
    CheckConstraint,
    Column,
    DateTime,
    Enum,
    Field,
    Index,
    SQLModel,
    func,
)

from app.enum.enum import RoleEnum


class Permission(SQLModel, table=True):
    """
    权限表(permissions)
    """

    __tablename__: str = "permission"

    id: int = Field(primary_key=True, default=None)
    name: str = Field(max_length=100, unique=True, description="权限名称(中文描述)")
    resource_type: str = Field(max_length=50, description="资源类型")
    action: str = Field(max_length=30, description="操作类型")
    scope: str = Field(max_length=30, description="作用范围")
    bit_position: int = Field(unique=True, description="在位图中的位置(用于高性能判断)")
    # created_at: 仅在插入时设为当前时间
    created_at: datetime = Field(sa_column=Column(DateTime, server_default=func.now()))

    # updated_at: 插入和每次更新时自动设为当前时间
    updated_at: datetime = Field(
        sa_column=Column(DateTime, server_default=func.now(), onupdate=func.now())
    )
    __table_args__ = (
        Index("index_unique_code", "resource_type", "action", "scope", unique=True),
    )


class Role(SQLModel, table=True):
    """
    角色表(role)
    """

    __tablename__: str = "role"
    id: int = Field(primary_key=True, default=None)
    name: str = Field(max_length=50, unique=True)
    code: str = Field(max_length=50, sa_column=Column(Enum(RoleEnum), unique=True))
    description: str | None = Field(default=None)
    # created_at: 仅在插入时设为当前时间
    created_at: datetime = Field(sa_column=Column(DateTime, server_default=func.now()))

    # updated_at: 插入和每次更新时自动设为当前时间
    updated_at: datetime = Field(
        sa_column=Column(DateTime, server_default=func.now(), onupdate=func.now())
    )


class RoleInheritance(SQLModel, table=True):
    """
    角色继承关系表(支持多继承)
    child_id -> 父角色列表
    """

    __tablename__: str = "role_inheritance"

    child_id: int = Field(foreign_key="role.id", primary_key=True)
    parent_id: int = Field(foreign_key="role.id", primary_key=True)
    created_at: datetime = Field(sa_column=Column(DateTime, server_default=func.now()))
    __table_args__ = (
        CheckConstraint("child_id != parent_id", name="child_id_not_equal_parent_id"),
    )


class RolePermission(SQLModel, table=True):
    """
    角色权限关联表(role_permissions)
    """

    __tablename__: str = "role_permission"
    role_id: int = Field(primary_key=True, foreign_key="role.id")
    permission_id: int = Field(primary_key=True, foreign_key="permission.id")
    created_at: datetime = Field(sa_column=Column(DateTime, server_default=func.now()))


class UserRole(SQLModel, table=True):
    """
    用户角色关联表(user_roles)
    """

    __tablename__: str = "user_role"
    user_id: int = Field(primary_key=True, foreign_key="user.id")
    role_id: int = Field(primary_key=True, foreign_key="role.id")
    granted_by: int | None = Field(default=None, foreign_key="user.id")
    granted_at: datetime = Field(sa_column=Column(DateTime, server_default=func.now()))
