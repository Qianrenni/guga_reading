# 使用官方 Python 3.11 slim 镜像作为基础
FROM python:3.11-slim

# 设置工作目录(root 用户的家目录通常是 /root,但也可以直接用 /app)
WORKDIR /app

# 安装必要的系统依赖(如需编译包,例如某些数据库驱动)
# 根据你的 requirements.txt 内容决定是否需要 gcc、build-essential 等
# RUN apt-get update && apt-get install -y gcc && rm -rf /var/lib/apt/lists/*

# 复制依赖文件并安装 Python 包(以 root 身份)
COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt
RUN pip install --no-cache-dir gunicorn

# 复制整个应用代码
# 只复制 /app 和 /static 目录
COPY /app ./app
# COPY /static ./static

# 暴露端口
EXPOSE 8000

# 直接在 CMD 中启动 Gunicorn(动态计算 workers)
CMD ["sh", "-c", "WORKERS=${WORKERS:-$(($(nproc 2>/dev/null || echo 2)+1))}; echo \"Starting Gunicorn with $WORKERS workers...\"; exec gunicorn -k uvicorn.workers.UvicornWorker app.main:app --bind 0.0.0.0:8000 --workers \"$WORKERS\" --worker-connections 1000 --timeout 60 --keep-alive 5"]