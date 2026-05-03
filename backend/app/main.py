import asyncio
import json
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.openapi.docs import (
    get_swagger_ui_html,
    get_swagger_ui_oauth2_redirect_html,
)
from fastapi.staticfiles import StaticFiles
from starlette.middleware.cors import CORSMiddleware
from starlette.responses import JSONResponse

from app.api.v1.admin import admin_router
from app.api.v1.author import author_router
from app.api.v1.book import book_router
from app.api.v1.captcha import captcha_router
from app.api.v1.right import right_router
from app.api.v1.shelf import shelf_router
from app.api.v1.statistic import statistic_router
from app.api.v1.token import token_router
from app.api.v1.user import user_router
from app.api.v1.user_reading_progress import user_reading_progress_router
from app.core.config import BASE_DIR, SETTING
from app.core.database import close_database, close_redis, init_redis
from app.core.error_handler import AppError
from app.core.scheduler import Scheduler
from app.core.task import SystemTask
from app.initial import init
from app.middleware.logging import logger
from app.middleware.rate_limit import RateLimitMiddleware
from app.models.response_model import ResponseCode
from app.services.recommend_service import book_recommend_service
from app.services.right_service import RightService

logger.debug(f"Setting: {SETTING.model_dump_json(indent=4)}")


@asynccontextmanager
async def life_span(_app: FastAPI):
    scheduler = Scheduler()
    scheduler.set_cron(
        SystemTask.collect_chapter_read_statistics.__name__,
        SystemTask.collect_chapter_read_statistics,
        "5 * * * *",
    )
    await asyncio.gather(
        book_recommend_service.load_model(),
        init_redis(),
        RightService.prepare_info(),
        init(),
    )
    scheduler.start()
    yield
    scheduler.shutdown()
    await asyncio.gather(
        close_redis(),
        close_database(),
    )


# 在 main.py 中注册
app = FastAPI(
    docs_url=None,
    lifespan=life_span,
)
STATIC_DIR = BASE_DIR / "static"

# 挂载静态文件目录
# 只有在目录存在时才挂载(避免测试时崩溃)
if STATIC_DIR.exists():
    app.mount("/static", StaticFiles(directory=str(STATIC_DIR)), name="static")
else:
    logger.warning(f"Static directory '{STATIC_DIR}' not found. Skipping static mount.")

app.add_middleware(
    CORSMiddleware,
    allow_origins=[
        x.strip() for x in SETTING.ALLOW_ORIGINS.split(",") if x.strip() != ""
    ],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
    expose_headers=["x-captcha-id"],  # 暴露自定义头部
)
if SETTING.IP_LIMIT_ENABLE != 0:
    app.add_middleware(
        RateLimitMiddleware,
        calls=SETTING.IP_LIMIT_COUNT,
        period=SETTING.IP_LIMIT_WINDOW,
        exclude_paths={"/docs", "/openapi.json", "/health"},  # 排除 Swagger 等
    )


if SETTING.ENV != "prod":

    @app.middleware("http")
    async def log_middleware(request: Request, call_next):
        response = await call_next(request)

        logger.info(
            f"{request.client.host} {request.method} {request.url} {response.status_code}"
        )
        return response


@app.exception_handler(AppError)
async def custom_exception_handler(request: Request, exc: AppError):
    logger.error(
        json.dumps(
            {
                "AppError": f"{exc}",
                "request": {
                    "host": f"{request.client.host}",
                    "method": f"{request.method}",
                    "url": f"{request.url}",
                    "headers": dict(request.headers),
                    "query_params": f"{request.query_params}",
                    "path_params": f"{request.path_params}",
                },
            },
            ensure_ascii=False,
            indent=4,
        )
    )
    return JSONResponse(
        status_code=exc.status_code,
        content={
            "code": ResponseCode.ERROR,
            "message": exc.message if exc.message else str(exc),
            "data": None,
        },
        media_type=exc.media_type,
    )


@app.exception_handler(ValueError)
async def value_error_handler(request: Request, exc: ValueError):
    logger.error(
        json.dumps(
            {
                "valueError": f"{exc}",
                "request": {
                    "host": f"{request.client.host}",
                    "method": f"{request.method}",
                    "url": f"{request.url}",
                    "headers": dict(request.headers),
                    "query_params": f"{request.query_params}",
                    "path_params": f"{request.path_params}",
                },
            },
            ensure_ascii=False,
            indent=4,
        )
    )
    return JSONResponse(
        status_code=400,
        content={"code": ResponseCode.ERROR, "message": str(exc), "data": None},
    )


@app.exception_handler(Exception)
async def exception_handler(request: Request, exc: Exception):
    logger.error(
        json.dumps(
            {
                "exception": f"{exc}",
                "request": {
                    "host": f"{request.client.host}",
                    "method": f"{request.method}",
                    "url": f"{request.base_url}",
                    "headers": dict(request.headers),
                    "query_params": f"{request.query_params}",
                    "path_params": f"{request.path_params}",
                },
            },
            ensure_ascii=False,
            indent=4,
        )
    )
    return JSONResponse(
        status_code=500,
        content={"code": ResponseCode.ERROR, "message": "服务器错误", "data": None},
    )


if SETTING.ENV != "prod":

    @app.get("/docs", include_in_schema=False)
    async def custom_swagger_ui_html():
        return get_swagger_ui_html(
            openapi_url=app.openapi_url,
            title=app.title + " - Swagger UI",
            oauth2_redirect_url=app.swagger_ui_oauth2_redirect_url,
            swagger_js_url="https://unpkg.com/swagger-ui-dist@5/swagger-ui-bundle.js",  # ← 国内能访问
            swagger_css_url="https://unpkg.com/swagger-ui-dist@5/swagger-ui.css",  # ← 国内能访问
        )

    @app.get(app.swagger_ui_oauth2_redirect_url, include_in_schema=False)
    async def swagger_ui_redirect():
        return get_swagger_ui_oauth2_redirect_html()


@app.get("/")
async def root():
    return {"message": "Hello World"}


#  获取 token  路由
app.include_router(token_router)
# 用户相关
app.include_router(user_router)
# 书籍相关
app.include_router(book_router)
# 书架相关
app.include_router(shelf_router)
# 阅读历史相关
app.include_router(user_reading_progress_router)
#  验证码相关
app.include_router(captcha_router)
# 权限相关
app.include_router(right_router)
# 作者相关
app.include_router(author_router)
# 作者数据相关
app.include_router(statistic_router)
# 管理相关
app.include_router(admin_router)
