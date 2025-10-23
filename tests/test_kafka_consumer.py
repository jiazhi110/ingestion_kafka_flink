#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Kafka消费者测试脚本
用于验证Kafka连接和消息消费功能
"""

import sys
import os
import json
import time
from mykafka.kafka_utils import get_producer, send_event

# 添加项目根目录到Python路径
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

def test_kafka_connection():
    """测试Kafka连接和基本消息发送/消费功能"""
    print("Testing Kafka connection...")
    
    # 从本地配置加载Kafka配置
    import yaml
    config_path = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 
                              'config', 'application-local.yaml')
    
    with open(config_path, 'r') as f:
        config = yaml.safe_load(f)
    
    kafka_config = config['kafka']
    bootstrap_servers = kafka_config['bootstrap_servers']
    topic = kafka_config['topic']
    security_config = kafka_config.get('security')
    
    # 创建生产者
    producer = get_producer(bootstrap_servers, security_config)
    
    # 发送测试消息
    test_event = {
        "date": int(time.time() * 1000),
        "user_id": 1001,
        "session_id": "test_session_001",
        "page_id": 1,
        "action_time": int(time.time() * 1000),
        "search_keyword": "test",
        "click_category_id": 1,
        "click_product_id": 1,
        "order_category_ids": "",
        "order_product_ids": "",
        "pay_category_ids": "",
        "pay_product_ids": "",
        "city_id": 1
    }
    
    try:
        # 发送测试消息
        send_event(producer, topic, test_event)
        print(f"Test message sent to topic '{topic}'")
        
        # 刷新生产者
        producer.flush()
        print("Producer flushed successfully")
        
        print("Kafka connection test completed successfully!")
        return True
        
    except Exception as e:
        print(f"Error during Kafka test: {e}")
        return False
    finally:
        # 关闭生产者
        if 'producer' in locals():
            producer.close()

if __name__ == "__main__":
    success = test_kafka_connection()
    if success:
        print("All tests passed!")
        sys.exit(0)
    else:
        print("Tests failed!")
        sys.exit(1)