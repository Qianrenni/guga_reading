# app/middleware/logging.py
import sys

from loguru import logger

from app.core.config import SETTING

logger.configure(
    handlers=[
        {
            "sink": "logs/app_{time:YYYY-MM-DD}.log",
            "level": "INFO" if SETTING.ENV == "prod" else "DEBUG",
            "format": "{time:YYYY-MM-DD HH:mm:ss} | {level} | {name}:{function}:{line} | {message}",
            "rotation": "00:00",
            "retention": "7 days",
            "compression": None,
            "enqueue": True,
            "backtrace": True,
            "diagnose": SETTING.ENV != "prod",
            "catch": True,
            # 只记录非错误级别的日志到通用日志文件
            "filter": lambda record: record["level"].no < logger.level("ERROR").no,
        },
        {
            "sink": "logs/error_{time:YYYY-MM-DD}.log",
            "level": "ERROR",
            "format": "{time:YYYY-MM-DD HH:mm:ss} | {level} | {name}:{function}:{line} | {message}",
            "rotation": "00:00",
            "retention": "7 days",
            "compression": None,
            "enqueue": True,
            "backtrace": True,
            "diagnose": SETTING.ENV != "prod",
            "catch": True,
            # 只记录错误级别的日志到错误日志文件
            "filter": lambda record: record["level"].no >= logger.level("ERROR").no,
        },
        {
            "sink": sys.stderr,
            "level": "INFO" if SETTING.ENV == "prod" else "DEBUG",
            "colorize": True,
            "format": "<green>{time:HH:mm:ss}</green> | <level>{level}</level> | <cyan>{message}</cyan>",
            "catch": True,
        },
    ],
    extra={"environment": SETTING.ENV},
)
