# 本地开发环境准备

## 目录结构

为了支持本地开发，我们创建了以下目录和文件：

```
ingestion_kafka_flink/
├── config/
│   └── application-local.yaml    # 本地开发配置
├── scripts/
│   ├── start_local_env.sh        # 启动本地环境
│   └── build_flink_job.sh        # 构建Flink作业
├── tests/
│   └── test_kafka_consumer.py    # Kafka连接测试
└── docs/
    └── (后续添加文档)
```

## 环境准备步骤

1. **启动本地环境**：
   ```bash
   ./scripts/start_local_env.sh
   ```

2. **构建Flink作业**：
   ```bash
   ./scripts/build_flink_job.sh
   ```

3. **运行Kafka测试**：
   ```bash
   python tests/test_kafka_consumer.py
   ```

## 配置说明

本地开发配置文件 `config/application-local.yaml` 包含：
- Kafka本地连接配置
- MinIO本地存储配置
- Flink本地调试配置

## 下一步建议

1. 完善Docker Compose配置以支持本地Kafka和MinIO
2. 添加更多测试用例
3. 创建本地Flink集群启动脚本
4. 添加数据验证脚本