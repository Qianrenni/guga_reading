from datetime import datetime

from sqlmodel import Column, DateTime, Field, PrimaryKeyConstraint, SQLModel, func


class Author(SQLModel, table=True):
    __tablename__: str = "author"
    id: int = Field(primary_key=True)
    user_id: int = Field(foreign_key="user.id", index=True, unique=True)
    name: str = Field(index=True, max_length=50, unique=True)
    # created_at: 仅在插入时设为当前时间
    created_at: datetime = Field(sa_column=Column(DateTime, server_default=func.now()))

    # updated_at: 插入和每次更新时自动设为当前时间
    updated_at: datetime = Field(
        sa_column=Column(DateTime, server_default=func.now(), onupdate=func.now())
    )


class AuthorBook(SQLModel, table=True):
    __tablename__: str = "author_book"
    author_id: int = Field(foreign_key="author.id", index=True)
    book_id: int = Field(foreign_key="book.id", index=True)
    created_at: datetime = Field(sa_column=Column(DateTime, server_default=func.now()))
    __table_args__ = (
        # 联合索引
        PrimaryKeyConstraint("author_id", "book_id"),
    )
