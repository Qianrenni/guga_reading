from datetime import datetime

from sqlalchemy import Column, Enum, Text
from sqlmodel import DateTime, Field, Index, SQLModel, func

from app.enum.enum import ActionEnum, BookDraftStatusEnum


class BookBase(SQLModel):
    """
    公共书籍属性
    """

    # 书名
    name: str = Field(index=True)
    # 作者
    author: str = Field(index=True)
    # 封面
    cover: str = Field(default="")
    # 描述
    description: str = Field(default="", sa_type=Text)
    # 分类
    category: str = Field(default="", index=True)
    # 标签
    tags: str = Field(default="")


class Book(BookBase, table=True):
    __tablename__: str = "book"
    id: int = Field(default=None, primary_key=True)
    # 章节数
    total_chapter: int = Field(default=0, index=True)
    # created_at: 仅在插入时设为当前时间
    created_at: datetime = Field(sa_column=Column(DateTime, server_default=func.now()))
    # updated_at: 插入和每次更新时自动设为当前时间
    updated_at: datetime = Field(
        sa_column=Column(DateTime, server_default=func.now(), onupdate=func.now())
    )
    # 字数
    words_cnt: int = Field(default=0, index=True)
    # 是否已完结
    is_ended: bool = Field(default=False, index=True)
    __table_args__ = (
        Index("idx_category_is_ended", "category", "is_ended"),
        Index("idx_category_words", "category", "words_cnt"),
    )

    @classmethod
    def sort_fields(cls):
        return [
            "id",
            "name",
            "author",
            "total_chapter",
            "created_at",
            "words_cnt",
            "is_ended",
        ]


class BookDraft(BookBase, table=True):
    __tablename__: str = "book_draft"
    id: int = Field(default=None, primary_key=True)
    # 特有字段
    action: ActionEnum = Field(
        default=ActionEnum.CREATE,
        sa_column=Column(Enum(ActionEnum), server_default=ActionEnum.CREATE.value),
    )
    book_id: int | None = Field(index=True, foreign_key="book.id", default=None)
    user_id: int = Field(index=True, foreign_key="user.id")
    status: BookDraftStatusEnum = Field(
        default=BookDraftStatusEnum.PENDING,
        sa_column=Column(
            Enum(BookDraftStatusEnum), server_default=BookDraftStatusEnum.PENDING.value
        ),
    )
    created_at: datetime = Field(sa_column=Column(DateTime, server_default=func.now()))
    updated_at: datetime = Field(
        sa_column=Column(DateTime, server_default=func.now(), onupdate=func.now())
    )

    __table_args__ = (
        Index("idx_book_draft_unique", "name", "user_id", "action", unique=True),
    )
