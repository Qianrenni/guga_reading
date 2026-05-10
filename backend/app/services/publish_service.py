import shutil
from itertools import groupby

from sqlmodel import select, update

from app.core.config import SETTING
from app.core.database import get_session_context
from app.enum.enum import BookStatusEnum
from app.middleware.logging import logger
from app.models.database.book import Book
from app.models.database.book_chapter import BookChapter
from app.services.chapter_store_service import ChapterStore


class PublishService:
    """发布审核通过的书籍内容服务"""

    @staticmethod
    async def publish_approved_content():
        async with get_session_context() as session:
            # 获取需要发布的书籍内容
            logger.info("开始发布审核通过的书籍内容")
            statement = select(Book).where(Book.status == BookStatusEnum.APPROVED)
            result = await session.exec(statement)
            books = list(result.all())
            books = [Book(**book.__dict__) for book in books]
            logger.debug(books)
            for book in books:
                if book.parent_id is not None:
                    update_statement = [
                        (
                            update(Book)
                            .where(Book.id == book.parent_id)
                            .values(
                                name=book.name,
                                description=book.description,
                                cover=book.cover,
                                tags=book.tags,
                                updated_at=book.updated_at,
                            )
                        ),
                        (
                            update(Book)
                            .where(Book.id == book.id)
                            .values(status=BookStatusEnum.PUBLISHED)
                        ),
                    ]
                    try:
                        for statement in update_statement:
                            await session.exec(statement)
                        src_path = (
                            SETTING.STATIC_TEMP_DIR_PATH
                            / "book"
                            / f"{book.id}"
                            / "cover.webp"
                        )
                        target_path = (
                            SETTING.STATIC_DIR_PATH
                            / "book"
                            / f"{book.parent_id}"
                            / "cover.webp"
                        )
                        target_path.parent.mkdir(parents=True, exist_ok=True)
                        shutil.move(src_path, target_path)
                        await session.commit()
                    except Exception as e:
                        logger.error(e)
                        await session.rollback()
                else:
                    update_statement = (
                        update(Book)
                        .where(Book.id == book.id)
                        .values(
                            status=BookStatusEnum.PUBLISHED,
                        )
                    )
                    try:
                        await session.exec(update_statement)
                        await session.commit()
                        src_path = (
                            SETTING.STATIC_TEMP_DIR_PATH
                            / "book"
                            / f"{book.id}"
                            / "cover.webp"
                        )
                        target_path = (
                            SETTING.STATIC_DIR_PATH
                            / "book"
                            / f"{book.id}"
                            / "cover.webp"
                        )
                        target_path.parent.mkdir(parents=True, exist_ok=True)
                        shutil.move(src_path, target_path)
                    except Exception as e:
                        logger.error(e)
                        await session.rollback()
            # 获取需要发布的章节
            statement = select(BookChapter).where(
                BookChapter.status == BookStatusEnum.APPROVED
            )
            result = await session.exec(statement)
            chapters = list(result.all())
            chapters = [BookChapter(**chapter.__dict__) for chapter in chapters]
            chapters = groupby(chapters, key=lambda x: x.book_id)
            for book_id, chapter_list in chapters:
                temp_store = ChapterStore(
                    book_id, SETTING.CONTENT_TEMP_DIR_PATH / "book"
                )
                target_store = ChapterStore(book_id, SETTING.CONTENT_DIR_PATH / "book")
                await temp_store._load_index()
                await target_store._load_index()
                for chapter in chapter_list:
                    # 新章节
                    update_statement = (
                        update(BookChapter)
                        .where(BookChapter.id == chapter.id)
                        .values(
                            status=BookStatusEnum.PUBLISHED,
                        )
                    )
                    await session.exec(update_statement)
                    if chapter.order > 0:
                        try:
                            await session.commit()
                            content = await temp_store.read_chapter(chapter.id)
                            await target_store.update_chapter(chapter.id, content)
                        except Exception as e:
                            logger.error(e)
                            await session.rollback()
                    else:
                        # 章节更新
                        statement = (
                            select(BookChapter)
                            .where(BookChapter.book_id == book_id)
                            .where(BookChapter.order == abs(chapter.order))
                        )
                        result = await session.exec(statement)
                        target_chapter = result.first()
                        target_chapter.title = chapter.title
                        target_chapter.word_count = chapter.word_count
                        target_id = target_chapter.id
                        try:
                            session.add(target_chapter)
                            await session.commit()
                            content = await temp_store.read_chapter(chapter.id)
                            await target_store.update_chapter(target_id, content)
                        except Exception as e:
                            logger.error(e)
                            await session.rollback()
                await temp_store.delete_chapter(chapter_id=chapter.id)
            logger.info("发布审核通过的书籍内容完成")
