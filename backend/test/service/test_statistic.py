from datetime import datetime, timedelta

import pytest

from app.core.config import SETTING
from app.services.statistic_service import StatisticService


@pytest.mark.asyncio
async def test_statistic():
    start_time = (
        datetime.now()
        .astimezone(SETTING.TIMEZONE)
        .replace(minute=0, second=0, microsecond=0)
    )
    end_time = start_time + timedelta(hours=1)
    await StatisticService.aggregate_hourly_statistics(
        start_time=start_time, end_time=end_time
    )
