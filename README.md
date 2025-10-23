# ingestion_kafka_flink

## 项目概述

本项目是一个完整的数据管道解决方案，用于从Kafka消费用户行为数据，通过Flink进行实时处理，并将结果存储到S3中。项目主要包括以下组件：

- Kafka数据生产者：模拟用户行为数据并发送到Kafka
- Flink流处理作业：消费Kafka数据，进行ETL处理，并写入S3
- 配置管理：统一管理Kafka和AWS相关的配置信息

## 项目架构

```
用户行为数据 → Kafka生产者 → Kafka集群 → Flink消费者 → S3存储
```

## 目录结构

```
ingestion_kafka_flink/
├── config/              # 配置文件目录
├── flink_jobs/          # Flink作业源代码
├── flink_lib/           # Flink依赖库
├── kafka_admin/         # Kafka管理工具
├── mock_data/           # 模拟数据生成器
├── mykafka/             # Kafka工具类
├── scripts/             # 启动脚本
├── test_data/           # 测试数据
├── requirements.txt     # Python依赖
├── Dockerfile           # 容器配置
└── docker-compose.yml   # 服务编排
```

## 快速开始

### 环境准备

1. 安装Python 3.8+
2. 安装Java 11+
3. 安装Maven 3.6+
4. 安装Docker和Docker Compose

### 安装依赖

```bash
# 安装Python依赖
pip install -r requirements.txt
```

### 启动服务

```bash
# 方法1: 使用提供的脚本启动本地开发环境（推荐）
./scripts/start_local_env.sh

# 方法2: 直接使用Docker Compose
docker-compose up -d

# 方法3: 分别启动各个组件
```

### 本地开发

有关本地开发的详细信息，请参阅：
- [本地开发指南](docs/LOCAL_DEVELOPMENT.md)
- [调试指南](docs/DEBUGGING.md)

## 配置说明

配置文件位于 `config/kafka_config.yaml`，包含以下主要配置项：

- Kafka Bootstrap Servers
- Topic名称
- 安全认证配置

## 使用说明

### 生成模拟数据

```bash
# 生成并发送测试数据到Kafka
python mock_data/user_actions_generator.py
```

### 构建Flink作业

```bash
# 方法1: 使用提供的脚本构建
./scripts/build_flink_job.sh

# 方法2: 手动构建
cd flink_jobs
mvn clean package
```

### 启动Flink作业

```bash
# 提交作业到本地Flink集群
flink run -m localhost:8081 flink_jobs/target/flink-uber-job-1.0-SNAPSHOT.jar
```

## 开发指南

### 项目结构改进建议

为了使项目更符合标准数据工程项目规范，建议进行以下改进：

1. 重新组织目录结构：
   ```
   ingestion_kafka_flink/
   ├── src/
   │   ├── ingestion/
   │   │   ├── kafka_producer/
   │   │   └── kafka_utils/
   │   ├── processing/
   │   │   └── flink_jobs/
   │   └── utils/
   ├── tests/
   ├── config/
   ├── data/
   ├── docs/
   ├── scripts/
   ├── requirements/
   ├── setup.py
   ├── README.md
   ├── Dockerfile
   └── docker-compose.yml
   ```

2. 添加测试目录和测试框架
3. 完善文档内容
4. 添加项目元数据文件
5. 改进配置管理方式

## 贡献指南

1. Fork项目
2. 创建功能分支
3. 提交更改
4. 发起Pull Request