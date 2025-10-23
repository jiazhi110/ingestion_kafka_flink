#!/bin/bash

# 启动本地开发环境
# 包括Kafka, MinIO, Flink等服务

set -e

echo "Starting local development environment..."

# 创建必要的目录
mkdir -p /tmp/flink/checkpoints /tmp/flink/savepoints

# 启动Docker环境（如果docker-compose.yml存在）
if [ -f "docker-compose.yml" ]; then
    echo "Starting services with docker-compose..."
    docker-compose up -d
    echo "Waiting for services to start..."
    sleep 30
else
    echo "docker-compose.yml not found. Please ensure Docker services are running manually."
fi

# 验证服务状态
echo "Checking service status..."
docker-compose ps

echo "Local development environment started successfully!"
echo "You can now run your Flink jobs locally."