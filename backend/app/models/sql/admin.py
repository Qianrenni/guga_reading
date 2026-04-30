# from datetime import datetime

# from sqlmodel import Column, DateTime, Field, PrimaryKeyConstraint, SQLModel, func


# class AuditBookChapter(SQLModel, table=True):
#     __tablename__: str = "audit_book_chapter"
#     book_chapter_draft_id: int = Field(
#         foreign_key="book_chapter_draft.id", primary_key=True
#     )
#     user_id: int = Field(foreign_key="user.id", primary_key=True)
#     created_at: datetime = Field(sa_column=Column(DateTime, server_default=func.now()))
