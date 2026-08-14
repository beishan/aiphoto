FROM docker.m.daocloud.io/pytorch/pytorch:2.3.0-cuda12.1-cudnn8-runtime

ARG APP_VERSION=0.2.1
ENV APP_VERSION=${APP_VERSION}
LABEL org.opencontainers.image.title="aiphoto AI Service" \
      org.opencontainers.image.version="${APP_VERSION}"

WORKDIR /app

# Use Chinese mirrors
RUN sed -i 's|http://archive.ubuntu.com|https://mirrors.aliyun.com|g' /etc/apt/sources.list && \
    sed -i 's|http://security.ubuntu.com|https://mirrors.aliyun.com|g' /etc/apt/sources.list

# Install system dependencies
RUN apt-get update && apt-get install -y \
    libgl1-mesa-glx \
    libglib2.0-0 \
    build-essential \
    wget unzip ffmpeg \
    && rm -rf /var/lib/apt/lists/*

# Models are never baked into the image or downloaded at startup.
RUN mkdir -p /models

COPY requirements.txt .
RUN --mount=type=cache,target=/root/.cache/pip \
    pip install -i https://mirrors.aliyun.com/pypi/simple/ \
    --trusted-host mirrors.aliyun.com -r requirements.txt

COPY . .

EXPOSE 8000

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
