# app/core/scheduler.py
from datetime import datetime, timedelta

from apscheduler.events import EVENT_JOB_ERROR, EVENT_JOB_EXECUTED
from apscheduler.schedulers.asyncio import AsyncIOScheduler
from apscheduler.triggers.cron import CronTrigger
from apscheduler.triggers.date import DateTrigger
from apscheduler.triggers.interval import IntervalTrigger

from app.core.config import SETTING
from app.middleware.logging import logger


class Scheduler:
    """
    Scheduler class for managing scheduled tasks.
    """

    def __init__(self):
        """
        Initialize the Scheduler class.
        """
        self.scheduler = AsyncIOScheduler(
            timezone=SETTING.TIMEZONE,
            job_defaults={
                "coalesce": True,  # 合并错过的执行
                "max_instances": 1,  # 同一任务最多一个实例运行
            },
        )

        def listener(event):
            logger.info(f"[Scheduler] Job {event}")

        self.scheduler.add_listener(listener, EVENT_JOB_EXECUTED | EVENT_JOB_ERROR)

    def start(self):
        """
        Start the scheduler.
        """

        self.scheduler.start()

    def set_interval(self, job_id, func, seconds, **kwargs):
        """
        Set an interval job.

        Args:
            job_id (str): The unique identifier for the job.
            func (callable): The callable to be executed.
            seconds (int): The interval in seconds.
            **kwargs: Additional keyword arguments for the job.
        """
        self.scheduler.add_job(
            id=job_id, func=func, trigger=IntervalTrigger(seconds=seconds), **kwargs
        )

    def set_timeout(self, job_id, func, seconds, **kwargs):
        """
        Set a timeout job.

        Args:
            job_id (str): The unique identifier for the job.
            func (callable): The callable to be executed.
            seconds (int): The timeout in seconds.
            **kwargs: Additional keyword arguments for the job.
        """
        target_time = datetime.now().astimezone(SETTING.TIMEZONE) + timedelta(
            seconds=seconds
        )
        self.scheduler.add_job(
            id=job_id, func=func, trigger=DateTrigger(run_date=target_time), **kwargs
        )

    def set_cron(self, job_id, func, cron, **kwargs):
        """
        Set a cron job.

        Args:
            job_id (str): The unique identifier for the job.
            func (callable): The callable to be executed.
            cron (str): The cron schedule.
            **kwargs: Additional keyword arguments for the job.
        """
        self.scheduler.add_job(
            id=job_id, func=func, trigger=CronTrigger.from_crontab(cron), **kwargs
        )

    def shutdown(self):
        if self.scheduler and self.scheduler.running:
            self.scheduler.shutdown(wait=True)
            self.scheduler = None
        logger.info("[Scheduler] Shutdown complete")
