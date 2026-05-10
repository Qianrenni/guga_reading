from datetime import datetime

from sqlalchemy import Index
from sqlmodel import Column, DateTime, Field, SQLModel, func


class UserReadingProgress(SQLModel, table=True):
    """用户阅读进度"""

    __tablename__: str = "user_reading_progress"
    id: int = Field(default=None, primary_key=True)
    user_id: int = Field(foreign_key="user.id", index=True)
    book_id: int = Field(foreign_key="book.id", index=True)
    last_chapter_id: int = Field(
        foreign_key="book_chapter.id", index=True
    )  # 👈 物理章节ID
    last_position: int = Field(default=0)  # 0-100 表示百分比,或字符偏移量
    last_read_at: datetime = Field(
        sa_column=Column(DateTime, default=func.now(), onupdate=func.now())
    )
    __table_args__ = (
        Index("index_user_book_unique", "user_id", "book_id", unique=True),
    )
