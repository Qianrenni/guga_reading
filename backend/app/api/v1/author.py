from typing import Annotated

from fastapi import APIRouter, Body, Depends, File, Form, Path, Query, UploadFile
from fastapi.background import BackgroundTasks
from sqlmodel.ext.asyncio.session import AsyncSession

from app.core.database import get_session
from app.depend.verify import verify_author_book
from app.enum.enum import ActionEnum, ResourceTypeEnum, ScopeEnum
from app.models.response_model import ResponseModel
from app.models.sql.book import Book, BookDraft
from app.models.sql.book_chapter import BookChapterBase, BookChapterDraft
from app.models.sql.statistics import ChapterReadStatistics
from app.models.sql.user import FullUser
from app.services.author_book_service import AuthorBookService
from app.services.book_service import BookService
from app.services.right_service import generate_permission_code, right_check

author_router = APIRouter(prefix="/author", tags=["作者"])


@author_router.get("/book", response_model=ResponseModel[list[Book]])
async def get_author_book(
    current_user: Annotated[
        FullUser,
        Depends(
            right_check(
                [
                    generate_permission_code(
                        resource=ResourceTypeEnum.BOOK,
                        action=ActionEnum.CREATE,
                        scope=ScopeEnum.OWN,
                    )
                ]
            )
        ),
    ],
    background_task: BackgroundTasks,
    database: Annotated[AsyncSession, Depends(get_session)],
):
    """
    获取作者图书列表
    :param current_user:  当前用户
    :param background_task:  后台任务
    :param database:  数据库连接
    :return:  ResponseModel[list[Book]]
    """
    data = await AuthorBookService.get_author_book_list(
        database=database, user=current_user, background_tasks=background_task
    )
    return ResponseModel[list[Book]](data=data)


@author_router.patch("/chapter", status_code=204)
async def update_author_book_chapter(
    current_user: Annotated[
        FullUser,
        Depends(
            right_check(
                [
                    generate_permission_code(
                        resource=ResourceTypeEnum.BOOK,
                        action=ActionEnum.UPDATE,
                        scope=ScopeEnum.OWN,
                    )
                ]
            )
        ),
    ],
    book_id: Annotated[int, Body(embed=True)],
    sort_order: Annotated[float, Body(embed=True)],
    content: Annotated[str, Body(embed=True)],
    title: Annotated[str, Body(embed=True)],
    is_draft: Annotated[bool, Body(embed=True)],
    database: Annotated[AsyncSession, Depends(get_session)],
    background_task: BackgroundTasks,
):
    """
    更新作者图书
    :param current_user:  当前用户
    :param book_id:  图书id
    :param sort_order: 排序key
    :param content:  内容
    :param title: 标题
    :param is_draft: 是否为草稿
    :param database:  数据库连接
    """
    await verify_author_book(
        id=book_id,
        is_draft=False,
        current_user=current_user,
        database=database,
    )
    await AuthorBookService.update_author_book_chapter(
        database=database,
        book_id=book_id,
        content=content,
        title=title,
        sort_order=sort_order,
        is_draft=is_draft,
        background_tasks=background_task,
    )
    return


@author_router.post("/chapter", status_code=201)
async def create_author_book_chapter(
    current_user: Annotated[
        FullUser,
        Depends(
            right_check(
                [
                    generate_permission_code(
                        resource=ResourceTypeEnum.BOOK,
                        action=ActionEnum.CREATE,
                        scope=ScopeEnum.OWN,
                    )
                ]
            )
        ),
    ],
    book_id: Annotated[int, Body(embed=True)],
    title: Annotated[str, Body(embed=True)],
    content: Annotated[str, Body(embed=True)],
    sort_order: Annotated[float, Body(embed=True)],
    database: Annotated[AsyncSession, Depends(get_session)],
    background_task: BackgroundTasks,
):
    """
    创建作者图书章节
    :param current_user:  当前用户
    :param book_id:  图书id
    :param sort_order: 排序key
    :param title:  标题
    :param content:  内容
    :param database:  数据库连接
    """
    await verify_author_book(
        id=book_id,
        current_user=current_user,
        database=database,
    )
    await AuthorBookService.create_author_book_chapter(
        database=database,
        book_id=book_id,
        title=title,
        content=content,
        sort_order=sort_order,
        background_tasks=background_task,
    )
    return


@author_router.post("/book", status_code=201)
async def create_author_book(
    current_user: Annotated[
        FullUser,
        Depends(
            right_check(
                [
                    generate_permission_code(
                        resource=ResourceTypeEnum.BOOK,
                        action=ActionEnum.CREATE,
                        scope=ScopeEnum.OWN,
                    )
                ]
            )
        ),
    ],
    name: Annotated[str, Form()],
    author: Annotated[str, Form()],
    cover: Annotated[UploadFile, File()],
    description: Annotated[str, Form()],
    category: Annotated[str, Form()],
    tags: Annotated[str, Form()],
    database: Annotated[AsyncSession, Depends(get_session)],
    background_task: BackgroundTasks,
):
    """
    创建作者图书
    :param current_user:  当前用户
    :param name:  图书名称
    :param author:  作者
    :param cover:  封面
    :param description:  描述
    :param category:  分类
    :param tags:  标签
    :param database:  数据库连接
    """
    await AuthorBookService.create_author_book(
        database=database,
        name=name,
        author=author,
        cover=cover,
        description=description,
        category=category,
        tags=tags,
        background_tasks=background_task,
        user=current_user,
    )
    return


@author_router.patch("/book", status_code=204)
async def update_author_book(
    current_user: Annotated[
        FullUser,
        Depends(
            right_check(
                [
                    generate_permission_code(
                        resource=ResourceTypeEnum.BOOK,
                        action=ActionEnum.UPDATE,
                        scope=ScopeEnum.OWN,
                    )
                ]
            )
        ),
    ],
    id: Annotated[int, Form()],
    name: Annotated[str, Form()],
    author: Annotated[str, Form()],
    description: Annotated[str, Form()],
    category: Annotated[str, Form()],
    tags: Annotated[str, Form()],
    is_draft: Annotated[bool, Form()],
    action: Annotated[ActionEnum, Form()],
    database: Annotated[AsyncSession, Depends(get_session)],
    cover: Annotated[UploadFile | None, File()] = None,
):
    """
    更新作者图书
    :param current_user:  当前用户
    :param book_id:  图书id
    :param name:  图书名称
    :param author:  作者
    :param cover:  封面
    :param description:  描述
    :param category:  分类
    :param tags:  标签
    :param is_draft: 是否为草稿
    :param database:  数据库连接
    """
    await verify_author_book(
        id=id,
        current_user=current_user,
        database=database,
        is_draft=is_draft,
    )
    await AuthorBookService.update_author_book(
        database=database,
        id=id,
        name=name,
        author=author,
        cover=cover,
        description=description,
        category=category,
        tags=tags,
        is_draft=is_draft,
        user=current_user,
        action=action,
    )
    return


@author_router.get(
    "/book-chapter-draft", response_model=ResponseModel[list[BookChapterDraft]]
)
async def get_author_book_chapter_draft(
    current_user: Annotated[
        FullUser,
        Depends(
            right_check(
                [
                    generate_permission_code(
                        resource=ResourceTypeEnum.BOOK,
                        action=ActionEnum.CREATE,
                        scope=ScopeEnum.OWN,
                    )
                ]
            )
        ),
    ],
    book_id: Annotated[int, Query(ge=-1)],
    database: Annotated[AsyncSession, Depends(get_session)],
    chapter_id: Annotated[int, Query(ge=-1)] = -1,
):
    """
    获取作者图书章节草稿,如果chapter_id为-1则返回所有章节,否则返回指定章节,如果book_id为-1则返回所有图书,否则返回指定图书
    :param current_user:  当前用户
    :param book_id:  图书id
    :param chapter_id:  章节id
    :param database:  数据库连接
    """
    if book_id != -1:
        await verify_author_book(
            id=book_id,
            current_user=current_user,
            database=database,
        )
    result = await AuthorBookService.get_author_book_chapter_draft(
        database=database, book_id=book_id, user=current_user, chapter_id=chapter_id
    )
    return ResponseModel[list[BookChapterDraft]](data=result)


@author_router.get("/book-draft", response_model=ResponseModel[list[BookDraft]])
async def get_author_book_draft(
    current_user: Annotated[
        FullUser,
        Depends(
            right_check(
                [
                    generate_permission_code(
                        resource=ResourceTypeEnum.BOOK,
                        action=ActionEnum.CREATE,
                        scope=ScopeEnum.OWN,
                    )
                ]
            )
        ),
    ],
    database: Annotated[AsyncSession, Depends(get_session)],
    id: Annotated[int, Query(ge=-1)] = -1,
):
    """
    获取作者图书草稿,如果id为-1则返回所有图书,否则返回指定图书
    :param current_user:  当前用户
    :param id:  图书id
    :param database:  数据库连接
    """
    result = await AuthorBookService.get_author_book_draft(
        database=database, user=current_user, id=id
    )
    return ResponseModel[list[BookDraft]](data=result)


@author_router.get("/book-catalog", response_model=ResponseModel[list[BookChapterBase]])
async def get_author_book_catalog(
    current_user: Annotated[
        FullUser,
        Depends(
            right_check(
                [
                    generate_permission_code(
                        resource=ResourceTypeEnum.BOOK,
                        action=ActionEnum.CREATE,
                        scope=ScopeEnum.OWN,
                    )
                ]
            )
        ),
    ],
    book_id: Annotated[int, Query()],
    database: Annotated[AsyncSession, Depends(get_session)],
    chapter_id: Annotated[int, Query(ge=-1)] = -1,
):
    """
    获取作者图书目录,如果chapter_id为-1则返回所有章节,否则返回指定章节
    :param current_user:  当前用户
    :param book_id:  图书id
    :param chapter_id:  章节id
    :param database:  数据库连接
    """
    await verify_author_book(
        id=book_id,
        current_user=current_user,
        database=database,
    )
    result = await BookService.get_catelog_by_id(
        book_id=book_id, database=database, chapter_id=chapter_id
    )
    return ResponseModel[list[BookChapterBase]](data=result)


@author_router.delete("/book-chapter/{book_id}", status_code=204)
async def delete_author_book_chapter(
    current_user: Annotated[
        FullUser,
        Depends(
            right_check(
                [
                    generate_permission_code(
                        resource=ResourceTypeEnum.BOOK,
                        action=ActionEnum.CREATE,
                        scope=ScopeEnum.OWN,
                    )
                ]
            )
        ),
    ],
    book_id: Annotated[int, Path()],
    is_draft: Annotated[bool, Query()],
    sort_orders: Annotated[list[float], Body(embed=True)],
    database: Annotated[AsyncSession, Depends(get_session)],
):
    """
    删除作者图书章节
    :param current_user:  当前用户
    :param book_id:  图书id
    :param sort_orders:  排序keys,从0开始
    :param is_draft:  是否是草稿
    :param database:  数据库连接
    """
    if len(sort_orders) == 0:
        return
    await verify_author_book(
        id=book_id,
        current_user=current_user,
        database=database,
    )
    await AuthorBookService.delete_author_book_chapter(
        database=database,
        book_id=book_id,
        sort_orders=sort_orders,
        is_draft=is_draft,
    )
    return


@author_router.get("/book-chapter", response_model=ResponseModel[str])
async def get_author_book_chapter(
    current_user: Annotated[
        FullUser,
        Depends(
            right_check(
                [
                    generate_permission_code(
                        resource=ResourceTypeEnum.BOOK,
                        action=ActionEnum.CREATE,
                        scope=ScopeEnum.OWN,
                    )
                ]
            )
        ),
    ],
    book_id: Annotated[int, Query()],
    sort_order: Annotated[float, Query()],
    is_draft: Annotated[bool, Query()],
    database: Annotated[AsyncSession, Depends(get_session)],
):
    """
    获取作者图书章节
    :param current_user:  当前用户
    :param book_id:  图书id
    :param sort_order:  排序key
    :param is_draft:  是否是草稿
    :param database:  数据库连接
    """
    await verify_author_book(
        id=book_id,
        current_user=current_user,
        database=database,
        is_draft=is_draft,
    )
    result = await AuthorBookService.get_author_book_chapter(
        database=database,
        book_id=book_id,
        sort_order=sort_order,
        is_draft=is_draft,
    )
    return ResponseModel[str](data=result)


@author_router.get(
    "/book-chapter-draft-item", response_model=ResponseModel[BookChapterDraft]
)
async def get_author_book_chapter_draft_item(
    current_user: Annotated[
        FullUser,
        Depends(
            right_check(
                [
                    generate_permission_code(
                        resource=ResourceTypeEnum.BOOK,
                        action=ActionEnum.CREATE,
                        scope=ScopeEnum.OWN,
                    )
                ]
            )
        ),
    ],
    book_id: Annotated[int, Query(ge=1)],
    sort_order: Annotated[float, Query(ge=0)],
    database: Annotated[AsyncSession, Depends(get_session)],
):
    """
    获取作者图书章节草稿
    :param current_user:  当前用户
    :param book_id:  图书id
    :param sort_order:  排序key
    :param database:  数据库连接
    """
    await verify_author_book(
        id=book_id,
        current_user=current_user,
        database=database,
    )
    result = await AuthorBookService.get_author_book_chapter_draft_item(
        database=database, book_id=book_id, sort_order=sort_order
    )
    return ResponseModel[BookChapterDraft](data=result)


@author_router.patch("/status/book-chapter", status_code=204)
async def submit_author_book_chapter(
    current_user: Annotated[
        FullUser,
        Depends(
            right_check(
                [
                    generate_permission_code(
                        resource=ResourceTypeEnum.BOOK,
                        action=ActionEnum.CREATE,
                        scope=ScopeEnum.OWN,
                    )
                ]
            )
        ),
    ],
    book_id: Annotated[int, Query()],
    sort_order: Annotated[float, Query()],
    database: Annotated[AsyncSession, Depends(get_session)],
):
    """
    提交作者图书章节
    :param current_user:  当前用户
    :param book_id:  图书id
    :param sort_order:  排序key
    :param database:  数据库连接
    """
    await verify_author_book(
        id=book_id,
        current_user=current_user,
        database=database,
    )
    await AuthorBookService.submit_author_book_chapter(
        database=database, book_id=book_id, sort_order=sort_order
    )


@author_router.patch("/status/book-draft", status_code=204)
async def submit_author_book(
    current_user: Annotated[
        FullUser,
        Depends(
            right_check(
                [
                    generate_permission_code(
                        resource=ResourceTypeEnum.BOOK,
                        action=ActionEnum.CREATE,
                        scope=ScopeEnum.OWN,
                    )
                ]
            )
        ),
    ],
    id: Annotated[int, Query(description="表ID")],
    database: Annotated[AsyncSession, Depends(get_session)],
):
    """
    提交作者图书
    :param current_user:  当前用户
    :param id:  图书id
    :param database:  数据库连接
    """
    await verify_author_book(
        id=id,
        current_user=current_user,
        database=database,
        is_draft=True,
    )
    await AuthorBookService.submit_author_book(database=database, id=id)


@author_router.delete("/book-draft", status_code=204)
async def delete_author_book_draft(
    current_user: Annotated[
        FullUser,
        Depends(
            right_check(
                [
                    generate_permission_code(
                        resource=ResourceTypeEnum.BOOK,
                        action=ActionEnum.CREATE,
                        scope=ScopeEnum.OWN,
                    )
                ]
            )
        ),
    ],
    id: Annotated[int, Query(description="表ID")],
    action: Annotated[str, Query(description="操作")],
    database: Annotated[AsyncSession, Depends(get_session)],
):
    """
    删除作者图书草稿
    :param current_user:  当前用户
    :param id:  图书id
    :param database:  数据库连接
    """
    await verify_author_book(
        id=id,
        current_user=current_user,
        database=database,
        is_draft=True,
    )
    await AuthorBookService.delete_author_book_draft(
        database=database, id=id, action=action
    )


@author_router.get(
    "/book-statistics", response_model=ResponseModel[list[ChapterReadStatistics]]
)
async def get_author_book_statistics(
    current_user: Annotated[
        FullUser,
        Depends(
            right_check(
                [
                    generate_permission_code(
                        resource=ResourceTypeEnum.BOOK,
                        action=ActionEnum.CREATE,
                        scope=ScopeEnum.OWN,
                    )
                ]
            )
        ),
    ],
    book_id: Annotated[int, Query(description="图书ID")],
    database: Annotated[AsyncSession, Depends(get_session)],
    chapter_id: Annotated[int, Query(description="章节ID")] = -1,
):
    """
    获取作者图书阅读数据
    Args:
        book_id (int): 图书ID
        database (AsyncSession): 数据库会话
    """
    await verify_author_book(
        id=book_id,
        current_user=current_user,
        database=database,
    )
    result = await AuthorBookService.get_author_book_statistics(
        database=database, book_id=book_id, chapter_id=chapter_id
    )
    return ResponseModel[list[ChapterReadStatistics]](data=result)
