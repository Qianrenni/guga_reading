import datetime

from pydantic import BaseModel
from sqlmodel import Column, DateTime, Field, SQLModel, func


class User(SQLModel, table=True):
    """
    用户表,数据库建模的类
    """

    __tablename__: str = "user"
    id: int = Field(default=None, primary_key=True)
    username: str = Field(index=True)
    password: str = Field(index=True)
    email: str = Field(index=True)
    is_active: bool = Field(default=True)
    avatar: str = Field(default="")
    created_at: datetime.datetime = Field(
        sa_column=Column(DateTime, server_default=func.now())
    )


class FullUser(BaseModel):
    id: int
    username: str
    password: str | None = None
    email: str
    is_active: bool
    avatar: str
    created_at: datetime.datetime | None = None
    # 权限
    right: list[int]
