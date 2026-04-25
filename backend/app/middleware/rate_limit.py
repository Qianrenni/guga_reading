# middleware/rate_limit.py
from fastapi import Request, status
from starlette.middleware.base import BaseHTTPMiddleware
from starlette.responses import JSONResponse

from app.core.database import get_redis
from app.models.response_model import ResponseCode


def get_client_ip(request: Request) -> str:
    # 优先使用 X-Forwarded-For(适用于 Nginx、负载均衡)
    if "X-Forwarded-For" in request.headers:
        ip = request.headers["X-Forwarded-For"].split(",")[0].strip()
    elif "X-Real-IP" in request.headers:
        ip = request.headers["X-Real-IP"]
    else:
        ip = request.client.host if request.client else "127.0.0.1"
    return ip


class RateLimitMiddleware(BaseHTTPMiddleware):
    def __init__(
        self,
        app,
        calls: int = 60,  # 允许请求次数
        period: int = 30,  # 时间窗口(秒)
        exclude_paths: set | None = None,  # 不限流的路径
    ):
        super().__init__(app)
        self.calls = calls
        self.period = period
        self.exclude_paths = exclude_paths or set()

    async def dispatch(self, request: Request, call_next):
        # 跳过不需要限流的路径
        redis_pool = await get_redis()
        if request.url.path in self.exclude_paths:
            return await call_next(request)

        # 获取客户端 IP(支持反向代理)
        client_ip = get_client_ip(request)
        key = f"rate_limit:{client_ip}:{request.url.path}"

        # 使用 Redis 的 INCR + EXPIRE 实现原子限流
        current = await redis_pool.incr(key)
        if current == 1:
            await redis_pool.expire(key, self.period)

        if current > self.calls:
            return JSONResponse(
                status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                content={
                    "code": ResponseCode.ERROR,
                    "message": "请求频率过高,请稍后重试",
                    "data": None,
                },
            )
        response = await call_next(request)
        return response
