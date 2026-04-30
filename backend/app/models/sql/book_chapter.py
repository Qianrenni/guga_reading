from datetime import datetime

from sqlalchemy import Column
from sqlmodel import DateTime, Enum, Field, SQLModel, func

from app.enum.enum import BookStatusEnum


class BookChapterBase(SQLModel):
    id: int = Field(default=None, primary_key=True)
    title: str = Field(default="")
    word_count: int = Field(nullable=False)
    created_at: datetime
    updated_at: datetime


class BookChapter(BookChapterBase, table=True):
    __tablename__: str = "book_chapter"
    book_id: int = Field(foreign_key="book.id", index=True)
    # created_at: 仅在插入时设为当前时间
    created_at: datetime = Field(sa_column=Column(DateTime, server_default=func.now()))

    # updated_at: 插入和每次更新时自动设为当前时间
    updated_at: datetime = Field(
        sa_column=Column(DateTime, server_default=func.now(), onupdate=func.now())
    )
    status: BookStatusEnum = Field(
        default=BookStatusEnum.PENDING,
        sa_column=Column(
            Enum(BookStatusEnum), server_default=BookStatusEnum.PENDING.value
        ),
    )
    is_active: bool = Field(default=True)
    parent_id: int | None = Field(default=None, foreign_key="book_chapter.id")
