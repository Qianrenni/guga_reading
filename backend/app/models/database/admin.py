from datetime import datetime

from sqlmodel import Column, DateTime, Field, PrimaryKeyConstraint, SQLModel, func


class AuditBookChapter(SQLModel, table=True):
    __tablename__: str = "audit_book_chapter"
    book_chapter_id: int = Field(foreign_key="book_chapter.id")
    user_id: int = Field(foreign_key="user.id")
    created_at: datetime = Field(sa_column=Column(DateTime, server_default=func.now()))
    # updated_at: 插入和每次更新时自动设为当前时间
    updated_at: datetime = Field(
        sa_column=Column(DateTime, server_default=func.now(), onupdate=func.now())
    )
    __table_args__ = (PrimaryKeyConstraint("book_chapter_id", "user_id"),)


class AuditBook(SQLModel, table=True):
    __tablename__: str = "audit_book"
    book_id: int = Field(foreign_key="book.id")
    user_id: int = Field(foreign_key="user.id")
    created_at: datetime = Field(sa_column=Column(DateTime, server_default=func.now()))
    # updated_at: 插入和每次更新时自动设为当前时间
    updated_at: datetime = Field(
        sa_column=Column(DateTime, server_default=func.now(), onupdate=func.now())
    )
    __table_args__ = (PrimaryKeyConstraint("book_id", "user_id"),)
