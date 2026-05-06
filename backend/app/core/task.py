from datetime import datetime, timedelta

from app.core.config import SETTING
from app.services.publish_service import PublishService
from app.services.statistic_service import StatisticService
from app.utils.distribute_lock import DistributedLock


class SystemTask:
    @staticmethod
    async def collect_chapter_read_statistics():
        async with DistributedLock(
            "collect_chapter_read_statistics", expire_time=60, blocking=False
        ) as lock:
            if lock:
                end_time = (
                    datetime.now()
                    .astimezone(SETTING.TIMEZONE)
                    .replace(minute=0, second=0, microsecond=0)
                )
                start_time = end_time - timedelta(hours=1)
                await StatisticService.aggregate_hourly_statistics(start_time, end_time)

    @staticmethod
    async def publish_approved_content():
        """定时任务发布审核通过的书籍内容"""
        async with DistributedLock(
            "publish_approved_content", expire_time=1800, blocking=False
        ) as lock:
            if lock:
                await PublishService.publish_approved_content()
