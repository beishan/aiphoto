FROM docker.m.daocloud.io/pytorch/pytorch:2.3.0-cuda12.1-cudnn8-runtime

WORKDIR /app

# Use Chinese mirrors
RUN sed -i 's|http://archive.ubuntu.com|https://mirrors.aliyun.com|g' /etc/apt/sources.list && \
    sed -i 's|http://security.ubuntu.com|https://mirrors.aliyun.com|g' /etc/apt/sources.list

# Install system dependencies
RUN apt-get update && apt-get install -y \
    libgl1-mesa-glx \
    libglib2.0-0 \
    build-essential \
    wget unzip \
    && rm -rf /var/lib/apt/lists/*

# Pre-download InsightFace buffalo_l model
RUN mkdir -p /root/.insightface/models && \
    wget --no-check-certificate -O /tmp/buffalo_l.zip \
      "https://gh-proxy.com/https://github.com/deepinsight/insightface/releases/download/v0.7/buffalo_l.zip" && \
    unzip -q /tmp/buffalo_l.zip -d /root/.insightface/models/ && \
    rm /tmp/buffalo_l.zip

# Pre-download YOLOv8n model
RUN mkdir -p /root/.ultralytics && \
    wget --no-check-certificate -O /root/.ultralytics/yolov8n.pt \
      "https://gh-proxy.com/https://github.com/ultralytics/assets/releases/download/v8.2.0/yolov8n.pt"

COPY requirements.txt .
RUN pip install --no-cache-dir -i https://pypi.tuna.tsinghua.edu.cn/simple --trusted-host pypi.tuna.tsinghua.edu.cn -r requirements.txt

# Copy pre-downloaded CLIP model (use /opt to avoid volume mount override)
COPY models_cache/clip/open_clip_pytorch_model.bin /opt/models/clip/open_clip_pytorch_model.bin

COPY . .

EXPOSE 8000

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
