# ingestion_kafka_flink/scripts/run_all.sh
#!/bin/bash

# --- 1. 启动 Docker Compose 服务 ---
echo "Starting Docker Compose services (Kafka, MinIO, Flink)..."
# -f 指定docker-compose文件路径，-d 后台运行
docker-compose -f ../docker-compose.yml up -d

# 等待所有Docker服务启动并稳定
# 这很重要，确保Kafka和Flink完全就绪
echo "Waiting for Docker services to be fully ready (approx. 40-60 seconds)..."
sleep 60 # 给予足够的时间让所有容器启动并初始化

# --- 2. 启动 Kafka 生产者 (在宿主机运行) ---
echo "Starting Kafka producer (user_actions_generator.py)..."
# `&` 符号表示在后台运行此脚本
python3 -m ../mock_data/user_actions_generator.py &
PRODUCER_PID=$! # 获取生产者进程ID，以便后续停止

# --- 3. 提交 PyFlink 任务 ---
echo "Submitting PyFlink job to Flink JobManager..."
# 确保 'flink' 命令在你的PATH中，或使用绝对路径 (例如 /path/to/your/flink-1.16.3/bin/flink)
# --target remote: 告诉flink客户端连接远程JobManager
# -m localhost:8081: JobManager的地址和端口 (映射到宿主机)
# --python: 指定Python脚本
flink run \
  --target remote \
  -m localhost:8081 \
  --python ../flink_jobs/process_user_action.py

# 注意：一旦Flink job提交成功，它会在Flink集群中持续运行。
# `flink run` 命令会一直阻塞直到任务完成或被取消。
# 如果你需要在后台运行 `flink run`，可以使用 `nohup flink run ... &`。
# 但对于首次测试，前台阻塞可以让你看到提交日志。

echo "PyFlink job submitted. Producer is running in background. Press Ctrl+C to stop this script."
echo "You can check Flink Web UI at http://localhost:8081"
echo "You can check MinIO Web UI at http://localhost:9001 (User: minioadmin, Pass: minioadmin)"

# 保持脚本运行，直到被手动中断
wait $PRODUCER_PID

# --- 清理 ---
echo "Stopping producer and Docker services..."
kill $PRODUCER_PID # 杀死生产者进程
docker-compose -f ../docker-compose.yml down # 停止并移除所有Docker容器

echo "All services stopped. Done."