from app.core.config import BASE_DIR, SETTING
from app.middleware.logging import logger


async def init():
    """
    应用初始化操作
    """
    for key in dir(SETTING):
        if key.endswith("PATH") and "FILE" not in key:
            value = getattr(SETTING, key)
            path = BASE_DIR / value
            path.mkdir(exist_ok=True, parents=True)
            setattr(SETTING, key, path)
            logger.debug(f"创建目录:{key}: {value}")
