# 调试指南

本文档介绍了如何调试ETL数据管道中的各个组件。

## Flink作业调试

### 1. 启用详细日志

修改 `config/log4j.properties` 文件以启用更详细的日志记录:

```properties
# 增加日志级别
logger.flink.level = DEBUG
logger.kafka.level = DEBUG
logger.job.level = DEBUG
```

### 2. 使用Flink Web UI

访问 http://localhost:8081 查看:
- 作业状态和拓扑图
- 检查点统计信息
- 背压监控
- 任务管理器日志

### 3. 本地运行Flink作业

在IDE中直接运行 `KafkaConsumerJob.java` 进行本地调试。

### 4. 检查点调试

在Flink作业中添加检查点配置以更好地调试状态:

```java
// 启用检查点
env.enableCheckpointing(5000); // 每5秒一次
env.getCheckpointConfig().setCheckpointingMode(CheckpointingMode.EXACTLY_ONCE);
env.getCheckpointConfig().setMinPauseBetweenCheckpoints(500);
env.getCheckpointConfig().setCheckpointTimeout(60000);
env.getCheckpointConfig().setMaxConcurrentCheckpoints(1);
```

## Kafka调试

### 1. 使用Kafka CLI工具

```bash
# 进入Kafka容器
docker exec -it kafka bash

# 列出所有主题
kafka-topics.sh --bootstrap-server localhost:9092 --list

# 查看主题详情
kafka-topics.sh --bootstrap-server localhost:9092 --describe --topic user_behavior

# 消费消息进行验证
kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic user_behavior --from-beginning
```

### 2. 使用Kafka UI

访问 http://localhost:8080:
- 查看集群信息
- 浏览主题和消息
- 监控消费者组状态

### 3. 生产者调试

在 `mock_data/user_actions_generator.py` 中添加更多日志:

```python
# 在send_event函数中添加详细日志
def send_event(producer, topic, event):
    try:
        print(f"Sending event to topic '{topic}': {json.dumps(event, indent=2)}")
        # ... existing code ...
    except Exception as e:
        print(f"Error producing message: {e}")
        traceback.print_exc()
```

## 数据验证

### 1. 检查输入数据

```bash
# 查看测试数据文件
head -n 10 test_data/user_visit_action.txt
```

### 2. 验证数据转换

在 `mock_data/user_actions_generator.py` 中添加转换验证:

```python
# 验证转换后的数据
print("Data validation:")
print(f"  Total records: {len(events)}")
print(f"  Date range: {min(e['date'] for e in events)} to {max(e['date'] for e in events)}")
print(f"  User ID range: {min(e['user_id'] for e in events)} to {max(e['user_id'] for e in events)}")
```

### 3. 检查输出数据

```bash
# 查看MinIO中的输出数据
# 通过MinIO Console或使用mc工具
mc ls local/flink-bucket/user_action/
```

## 性能调优

### 1. 并行度调整

在本地开发时，可以调整Flink作业的并行度:

```java
// 在KafkaConsumerJob.java中
env.setParallelism(2); // 根据本地资源调整
```

### 2. 检查点间隔

```java
// 调整检查点间隔以适应本地开发
env.enableCheckpointing(10000); // 10秒一次，比生产环境更长
```

### 3. 内存配置

在 `docker-compose.yml` 中调整Flink TaskManager的内存:

```yaml
taskmanager:
  environment:
    FLINK_PROPERTIES_taskmanager.memory.process.size: "2048m"  # 增加内存
```

## 常见错误和解决方案

### 1. "Could not find any format factory" 错误

**原因**: 缺少必要的连接器依赖或服务发现文件未正确合并。

**解决方案**:
1. 确保 `pom.xml` 中包含了所有必要的连接器依赖
2. 确保 `maven-shade-plugin` 配置了 `ServicesResourceTransformer`

### 2. Kafka连接失败

**原因**: 网络配置或安全认证问题。

**解决方案**:
1. 检查 `bootstrap.servers` 配置是否正确
2. 验证Kafka容器是否正常运行
3. 检查安全认证配置

### 3. S3/MinIO写入失败

**原因**: 认证信息或端点配置错误。

**解决方案**:
1. 验证MinIO凭据和端点配置
2. 检查网络连接
3. 确保目标存储桶存在

### 4. 数据类型转换错误

**原因**: 测试数据中的空值或格式问题。

**解决方案**:
1. 在数据转换时添加更完善的错误处理
2. 使用 `fillna()` 处理空值
3. 添加数据验证步骤

## 调试工具

### 1. 日志分析

```bash
# 实时查看Flink日志
docker-compose logs -f jobmanager

# 查看特定时间段的日志
docker-compose logs --since "2023-01-01" jobmanager
```

### 2. 性能监控

使用Flink Web UI的Metrics选项卡监控:
- 吞吐量
- 延迟
- 背压
- 检查点持续时间

### 3. 内存分析

在Flink配置中启用内存监控:
```yaml
env.getCheckpointConfig().setCheckpointStorage("file:///tmp/flink/checkpoints");
```

然后分析检查点数据以了解状态大小。

## 最佳实践

1. **本地开发时使用小数据集**: 使用测试数据的子集进行快速迭代
2. **启用详细日志**: 在开发阶段启用DEBUG级别日志
3. **定期清理环境**: 使用 `stop_local_env.sh` 脚本清理资源
4. **版本控制配置**: 将本地配置与生产配置分离
5. **编写自动化测试**: 创建测试脚本验证各个组件的功能