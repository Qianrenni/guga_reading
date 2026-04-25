from datetime import datetime

from sqlalchemy import Column, Index
from sqlmodel import DateTime, Enum, Field, SQLModel, func

from app.enum.enum import ActionEnum, BookDraftStatusEnum


class BookChapterBase(SQLModel):
    id: int = Field(default=None, primary_key=True)
    title: str = Field(default="")
    sort_order: float = Field(index=True, nullable=False)
    word_count: int = Field(nullable=False)
    created_at: datetime
    updated_at: datetime


class BookChapter(BookChapterBase, table=True):
    __tablename__: str = "book_chapter"
    book_id: int = Field(foreign_key="book.id", index=True)
    # 添加联合唯一索引(防止同一本书章节排序重复)
    __table_args__ = (
        Index("index_book_sort_unique", "book_id", "sort_order", unique=True),
    )
    # created_at: 仅在插入时设为当前时间
    created_at: datetime = Field(sa_column=Column(DateTime, server_default=func.now()))

    # updated_at: 插入和每次更新时自动设为当前时间
    updated_at: datetime = Field(
        sa_column=Column(DateTime, server_default=func.now(), onupdate=func.now())
    )


class BookChapterDraft(BookChapterBase, table=True):
    __tablename__: str = "book_chapter_draft"
    action: ActionEnum = Field(
        default=ActionEnum.CREATE,
        sa_column=Column(Enum(ActionEnum), server_default=ActionEnum.CREATE.value),
    )
    sort_order: float = Field(index=False, nullable=False)
    book_id: int = Field(foreign_key="book.id", index=True)
    status: BookDraftStatusEnum = Field(
        default=BookDraftStatusEnum.PENDING,
        sa_column=Column(
            Enum(BookDraftStatusEnum), server_default=BookDraftStatusEnum.PENDING.value
        ),
    )
    # created_at: 仅在插入时设为当前时间
    created_at: datetime = Field(sa_column=Column(DateTime, server_default=func.now()))

    # updated_at: 插入和每次更新时自动设为当前时间
    updated_at: datetime = Field(
        sa_column=Column(DateTime, server_default=func.now(), onupdate=func.now())
    )
    __table_args__ = (
        Index("index_book_chapter_draft_unique", "book_id", "sort_order", unique=True),
    )
