from datetime import datetime

from sqlalchemy import Index
from sqlmodel import Column, DateTime, Field, SQLModel, func


class Shelf(SQLModel, table=True):
    __tablename__: str = "shelf"
    id: int = Field(default=None, primary_key=True)
    user_id: int = Field(foreign_key="user.id", index=True)
    book_id: int = Field(foreign_key="book.id", index=True)
    created_at: datetime = Field(sa_column=Column(DateTime, server_default=func.now()))
    # 联合唯一索引:一个用户不能重复添加同一本书到书架
    __table_args__ = (
        Index("index_user_book_unique", "user_id", "book_id", unique=True),
    )
