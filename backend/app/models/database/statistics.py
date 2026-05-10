from datetime import datetime

from sqlmodel import Column, DateTime, Enum, Field, Index, SQLModel, func

from app.enum.enum import ReportEnum


class ChapterReadStatistics(SQLModel, table=True):
    __tablename__: str = "chapter_read_statistics"

    id: int | None = Field(default=None, primary_key=True)

    book_id: int = Field(foreign_key="book.id", index=True)
    chapter_id: int = Field(foreign_key="book_chapter.id", index=True)  # 新增:章节ID

    # 统计窗口:对齐到小时(如 2026-02-05 14:00:00)
    hour_start: datetime = Field(
        sa_column=Column(
            DateTime,
            nullable=False,
            comment="Hour-aligned timestamp (e.g., '2026-02-05 14:00:00')",
        )
    )

    # UV: 该小时内阅读此章节的独立用户数(核心指标)
    unique_reader_count: int = Field(default=0, ge=0)

    # PV: 该小时内此章节被加载/访问的总次数(包括同一用户多次进入)
    page_view_count: int = Field(default=0, ge=0)

    # 用户在此章节的停留时长(秒)
    total_duration: float = Field(default=0, ge=0.0)

    # 复合唯一约束:确保每本书每章每小时只有一条记录
    __table_args__ = (
        Index("uq_chapter_hour", "book_id", "chapter_id", "hour_start", unique=True),
        Index("ix_hour_start", "hour_start"),
        Index(
            "ix_book_hour", "book_id", "hour_start"
        ),  # 快速查某本书所有章节的小时数据
    )


class UserReadEvent(SQLModel, table=True):
    """用户阅读行为日志(用于统计 PV/UV)"""

    __tablename__: str = "user_read_event"

    id: int = Field(default=None, primary_key=True)

    user_id: int = Field(foreign_key="user.id")
    book_id: int = Field(foreign_key="book.id")
    chapter_id: int = Field(foreign_key="book_chapter.id")  # 被访问的章节

    # 事件发生时间(精确到秒)
    event_time: datetime = Field(sa_column=Column(DateTime, server_default=func.now()))
    event_type: ReportEnum = Field(
        sa_column=Column(Enum(ReportEnum), nullable=False, comment="Event type")
    )

    # 索引优化
    __table_args__ = (
        Index("ix_event_time", "event_time"),
        Index("ix_user_book_chapter", "user_id", "book_id", "chapter_id", "event_time"),
        Index("ix_book_chapter_time", "book_id", "chapter_id", "event_time"),
    )
