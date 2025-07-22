# ingestion_kafka_flink/kafka/kafka_utils.py
from kafka import KafkaProducer
import json

def get_producer(bootstrap_servers):
    # 创建Kafka生产者实例
    return KafkaProducer(
        bootstrap_servers=bootstrap_servers,
        value_serializer=lambda v: json.dumps(v).encode('utf-8') # 自动将字典序列化为JSON字节
    )

def send_event(producer, topic, event):
    # 发送事件到Kafka，确保发送成功
    producer.send(topic, event).add_callback(lambda _: print(f"Sent: {event}")) \
                               .add_errback(lambda ex: print(f"Failed to send: {event} with error: {ex}"))
    producer.flush() # 立即发送所有挂起的消息