# app/core/config.py
from pathlib import Path
from zoneinfo import ZoneInfo

from pydantic_settings import BaseSettings

# 获取项目根目录(假设 main.py 在 app/ 目录下)
BASE_DIR = Path(__file__).resolve().parent.parent.parent  # 指向项目根目录


class Config(BaseSettings):
    ENV: str = "dev"
    PROTOCOL: str
    # Database
    MYSQL_DSN: str
    REDIS_URL: str

    # Security
    SECRET_KEY: str
    ALGORITHM: str = "HS256"
    # ACCESS_TOKEN_EXPIRE_有效期
    ACCESS_TOKEN_EXPIRE: int = 60 * 60 * 12
    # refresh_token 有效期
    REFRESH_TOKEN_EXPIRE: int = 7 * 24 * 60 * 60
    # 邮箱验证 有效期
    EMAIL_VERIFY_EXPIRE: int = 5 * 60
    #  验证码 有效期
    CAPTCHA_EXPIRE: int = 2 * 60
    # 分表
    BOOK_SHARD_COUNT: int = 64

    #  书籍缓存
    BOOK_CACHE_EXPIRE: int = 60 * 30 * 1 * 1
    # SERVER_URL
    SERVER_URL: str = "http://localhost:8000"
    # 邮箱授权码
    EMAIL_CODE: str
    # 邮箱账号
    EMAIL_ACCOUNT: str
    # 静态文件目录
    STATIC_DIR_PATH: str = "static"
    # 静态书籍文件目录
    STATIC_BOOK_DIR_PATH: str = "static/book"
    # 静态暂存文件目录
    STATIC_TEMP_DIR_PATH: str = "static/temp"
    # 存储目录
    CONTENT_DIR_PATH: str = "store"
    # 书籍文件目录
    BOOK_DIR_PATH: str = "store/book"
    # 数据暂存目录
    CONTENT_TEMP_DIR_PATH: str = "store/temp"
    # 推荐Book文件地址
    RECOMMEND_BOOK_FILE_PATH: str = "store/book.json"
    # 推荐模型文件地址
    RECOMMEND_MODEL_FILE_PATH: str = "store/model.pkl.npz"
    # 是否开始IP限流
    IP_LIMIT_ENABLE: int = 1
    # IP限流时间窗口
    IP_LIMIT_WINDOW: int = 60
    # IP限流次数
    IP_LIMIT_COUNT: int = 30

    # 回源函数窗口时间(S)
    FALL_BACK_FUNCTION_WINDOW: int = 10

    # 权限值的位数
    PERMISSION_BIT_LENGTH: int = 32

    # 权限缓存有效期
    PERMISSION_CACHE_EXPIRE: int = 60 * 60 * 24 * 7

    # 章节编码方式
    CHAPTER_ENCODING: str = "utf-8"

    # 时区
    TIMEZONE: ZoneInfo = ZoneInfo("UTC")
    # redis客户端连接数
    REDIS_CLIENT_POOL_SIZE: int = 50
    # redis连接满时等待时间
    REDIS_WAIT_TIMEOUT: int = 3
    # 允许的源 ,分割
    ALLOW_ORIGINS: str = "*"

    class Config:
        env_file = str(BASE_DIR / ".env")
        case_sensitive = True


SETTING = Config()
