from datetime import datetime
from itertools import groupby

from sqlmodel import delete, func, insert, select
from sqlmodel.ext.asyncio.session import AsyncSession

from app.core.config import SETTING
from app.core.database import get_session_context
from app.core.error_handler import AppError
from app.enum.enum import ReportEnum
from app.middleware.logging import logger
from app.models.sql.statistics import ChapterReadStatistics, UserReadEvent
from app.models.sql.user import FullUser


class StatisticService:
    @staticmethod
    async def post_book_chapter(
        book_id: int,
        chapter_id: int,
        event_type: ReportEnum,
        user: FullUser,
        database: AsyncSession,
    ):
        """
        上报阅读数据
        :param book_id: 书本id
        :param chapter_id: 章节id
        :param event_type: 事件类型
        :param user: 用户
        :param database: 数据库
        """
        statament = insert(UserReadEvent).values(
            book_id=book_id,
            chapter_id=chapter_id,
            event_type=event_type,
            user_id=user.id,
        )

        try:
            result = await database.exec(statament)
            if result.rowcount == 0:
                raise AppError(message="上报阅读数据失败", status_code=500)
            await database.commit()
        except Exception as e:
            await database.rollback()
            logger.error(e)

    @staticmethod
    async def aggregate_hourly_statistics(start_time: datetime, end_time: datetime):
        """
        将指定时间段内的 UserReadEvent 聚合到 ChapterReadStatistics
        Args:
            - start_time (datetime): 开始时间
            - end_time (datetime): 结束时间
        """
        async with get_session_context() as session:
            try:
                # 1. 统计 PV 和 UV (SQL 层聚合)
                # 只查询本小时内的 ENTER 事件
                pv_uv_query = (
                    select(
                        UserReadEvent.book_id,
                        UserReadEvent.chapter_id,
                        func.count(UserReadEvent.id).label("pv"),
                        func.count(func.distinct(UserReadEvent.user_id)).label("uv"),
                    )
                    .where(
                        UserReadEvent.event_type == ReportEnum.ENTER,
                        UserReadEvent.event_time >= start_time,
                        UserReadEvent.event_time < end_time,
                    )
                    .group_by(UserReadEvent.book_id, UserReadEvent.chapter_id)
                )
                pv_uv_result = await session.exec(pv_uv_query)

                # 初始化统计地图
                statistic_map = {}
                for row in pv_uv_result:
                    statistic_map[(row[0], row[1])] = {
                        "pv": row[2] or 0,
                        "uv": row[3] or 0,
                        "total_duration": 0,
                    }
                delete_statement = delete(UserReadEvent).where(
                    UserReadEvent.event_time >= start_time,
                    UserReadEvent.event_time < end_time,
                )
                # 如果没有 ENTER 事件,直接清理旧数据并退出
                if not statistic_map:
                    await session.exec(delete_statement)
                    await session.commit()
                    logger.info(
                        f"[ChapterReadStatistics] aggregate_hourly_statistics {start_time} to {end_time} no data"
                    )
                    return

                # 2. 计算停留时长 (Python 层逻辑 - 严格窗口)
                # 2.1 获取本小时内所有 ENTER 事件详情
                events_query = (
                    select(
                        UserReadEvent.user_id,
                        UserReadEvent.book_id,
                        UserReadEvent.chapter_id,
                        UserReadEvent.event_type,
                        UserReadEvent.event_time,
                    )
                    .where(
                        UserReadEvent.event_time >= start_time,
                        UserReadEvent.event_time < end_time,
                    )
                    .order_by(
                        UserReadEvent.user_id,
                        UserReadEvent.book_id,
                        UserReadEvent.chapter_id,
                        UserReadEvent.event_time,
                    )
                )
                events = await session.exec(events_query)
                events = list(events.all())
                events = groupby(events, lambda x: (x[0], x[1], x[2]))
                for key, group in events:
                    _, book_id, chapter_id = key
                    total_duration = 0
                    last = start_time
                    for event in group:
                        event_type = event[3]
                        event_time = event[4].astimezone(SETTING.TIMEZONE)
                        if event_type == ReportEnum.ENTER:
                            last = event_time
                        else:
                            total_duration += (event_time - last).total_seconds()
                            last = event_time
                    total_duration = total_duration or 1
                    statistic_map[(book_id, chapter_id)]["total_duration"] += (
                        total_duration
                    )
                batch_data = [
                    ChapterReadStatistics(
                        book_id=key[0],
                        chapter_id=key[1],
                        hour_start=start_time,
                        page_view_count=value["pv"],
                        unique_reader_count=value["uv"],
                        total_duration=value["total_duration"],
                    )
                    for key, value in statistic_map.items()
                ]
                session.add_all(batch_data)
                await session.exec(delete_statement)
                await session.commit()
                logger.info(
                    f"[ChapterReadStatistics] aggregate_hourly_statistics {start_time} to {end_time} succeed"
                )
            except Exception as e:
                await session.rollback()
                raise e
