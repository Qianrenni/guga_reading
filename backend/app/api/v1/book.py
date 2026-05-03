from typing import Annotated

from fastapi import APIRouter, BackgroundTasks, Path
from fastapi.params import Depends, Query
from pydantic import Field
from sqlmodel.ext.asyncio.session import AsyncSession

from app.core.database import get_session
from app.core.security import get_current_user
from app.models.response_model import ResponseModel
from app.models.sql.book import Book
from app.schema.book import BookCatalogItemResponseModel
from app.schema.common import CountResponseModel
from app.services.book_service import BookService
from app.services.recommend_service import book_recommend_service
from app.utils.sort import SortItem

book_router = APIRouter(prefix="/book", tags=["book"])


@book_router.get("/category/count", response_model=ResponseModel[CountResponseModel])
async def get_book_category_count(
    database: Annotated[AsyncSession, Depends(get_session)],
    category: Annotated[
        str,
        Query(
            ..., title="category", description="category", min_length=1, max_length=50
        ),
    ],
):
    """
    获取图书分类下的图书数量
    :param database:    数据库会话
    :param category:     分类
    :return:             图书数量
    """
    result = await BookService.get_category_count(database=database, category=category)
    return ResponseModel[CountResponseModel](data=CountResponseModel(count=result))


@book_router.get("/category", response_model=ResponseModel[list[str]])
async def get_book_category(database: Annotated[AsyncSession, Depends(get_session)]):
    """
    获取图书分类
    :param database:    数据库会话
    :return:    分类列表
    """
    result = await BookService.get_category(database=database)
    return ResponseModel[list[str]](data=result)


@book_router.get("/recommend", response_model=ResponseModel[list[Book]])
async def recommend_book(
    database: Annotated[AsyncSession, Depends(get_session)],
    background_tasks: BackgroundTasks,
    query: Annotated[
        str, Query(..., title="query", description="query", min_length=1, max_length=50)
    ],
):
    """
    获取推荐图书
    :param database:         数据库会话
    :param background_tasks 后台任务
    :param query 查询内容
    :return:                   推荐图书列表
    """
    result = await book_recommend_service.recommend(
        database=database, background_tasks=background_tasks, query_tags_str=query
    )
    books = await BookService.get_book_by_list(
        database=database,
        background_tasks=background_tasks,
        book_ids=[item[0] for item in result if item[0] > 0],
    )
    return ResponseModel[list[Book]](data=books)


@book_router.get("/search", response_model=ResponseModel[list[Book]])
async def search_book(
    database: Annotated[AsyncSession, Depends(get_session)],
    q: Annotated[
        str, Query(..., title="q", description="q", min_length=1, max_length=100)
    ],
):
    """
    搜索图书
    :param database:         数据库会话
    :param q:                 搜索关键字
    :return:                  图书列表
    """
    result = await BookService.search_book(keyword=q, database=database)
    return ResponseModel[list[Book]](data=result)


@book_router.get("/total", response_model=ResponseModel[CountResponseModel])
async def get_books_total_count(
    database: Annotated[AsyncSession, Depends(get_session)],
):
    """
    获取图书总数
    :param database:         数据库会话
    :return:                   图书总数
    """
    result = await BookService.get_books_total_count(database=database)
    return ResponseModel[CountResponseModel](data=CountResponseModel(count=result))


@book_router.get("/select", response_model=ResponseModel[list[Book]])
async def get_book_select(
    database: Annotated[AsyncSession, Depends(get_session)],
    category: Annotated[
        str,
        Query(
            ..., title="category", description="category", min_length=1, max_length=50
        ),
    ],
    offset: Annotated[int, Query(..., title="offset", description="offset", gt=-1)] = 0,
    limit: Annotated[int, Query(..., title="limit", description="limit", gt=-1)] = 10,
    sort: Annotated[list[str] | None, Query()] = None,  # ← 新增
):
    """
    获取图书精选
    :param database:    数据库会话
    :param category:     分类
    :param offset:       偏移量
    :param limit:        数量限制
    :param sort:         排序字段
        sort=id:-1
        sort=name:1
        sort=words_cnt:-1
        ......
    :return:             图书列表
    """
    if not sort:
        sort_condition = {"id": 1, "created_at": -1}
    else:
        sort_condition = {}
        for s in sort:
            item = SortItem.from_str(s)
            sort_condition[item.field] = item.order

    result = await BookService.get_book_select(
        category=category,
        offset=offset,
        limit=limit,
        database=database,
        sorted_condition=sort_condition,
    )
    return ResponseModel[list[Book]](data=result)


@book_router.get("/list", response_model=ResponseModel[list[Book]])
async def get_book_list(
    book_ids: Annotated[
        list[Annotated[int, Field(gt=0)]],
        Query(
            title="book_ids",
            description="List of book IDs to fetch",
        ),
    ],
    database: Annotated[AsyncSession, Depends(get_session)],
    background_tasks: BackgroundTasks,
):
    """
    获取图书列表
    :param book_ids:      图书ID列表
    :param database:      数据库会话
    :param background_tasks:  后台任务
    :return:      图书列表
    """
    result = await BookService.get_book_by_list(
        book_ids=book_ids, database=database, background_tasks=background_tasks
    )
    return ResponseModel[list[Book]](data=result)


@book_router.get("/{book_id}", response_model=ResponseModel[Book])
async def get_book(
    database: Annotated[AsyncSession, Depends(get_session)],
    book_id: int = Path(..., title="book_id", description="book_id", gt=0),
):
    """
    获取图书信息
    :param database:     数据库会话
    :param book_id:       图书ID
    :return:      图书信息
    """
    result = await BookService.get_book_by_id(book_id=book_id, database=database)
    return ResponseModel[Book](data=result)


@book_router.get(
    "/toc/{book_id}", response_model=ResponseModel[list[BookCatalogItemResponseModel]]
)
async def get_book_toc(
    database: Annotated[AsyncSession, Depends(get_session)],
    book_id: int = Path(..., title="book_id", description="book_id", gt=0),
):
    """
    获取图书目录
    :param database:      数据库会话
    :param book_id:         图书ID
    :return:         图书目录
    """
    result = await BookService.get_book_toc_by_id(book_id=book_id, database=database)
    return ResponseModel[list[BookCatalogItemResponseModel]](data=result)


@book_router.get(
    "/chapter/{chapter_id}",
    dependencies=[Depends(get_current_user)],
    response_model=ResponseModel[str],
)
async def get_book_chapter(
    chapter_id: Annotated[
        int, Path(..., title="chapter_id", description="chapter_id", gt=0)
    ],
    book_id: Annotated[int, Query(..., title="book_id", description="book_id", gt=0)],
):
    """
    获取图书章节
    :param database:         数据库会话
    :param chapter_id:         章节ID
    :param book_id:           图书ID
    :return:                  章节内容
    """
    result = await BookService.book_chapter_read_from_file(
        book_id=book_id,
        chapter_id=chapter_id,
    )
    return ResponseModel[str](data=result)
