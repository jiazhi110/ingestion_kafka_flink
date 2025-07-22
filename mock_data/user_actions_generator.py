# ingestion_kafka_flink/mock_data/user_actions_generator.py
import json, time, random
import yaml
import os
import pandas as pd
from mykafka.kafka_utils import get_producer,send_event # 导入辅助函数

# 从配置文件加载Kafka和MinIO配置
current_dir = os.path.dirname(os.path.abspath(__file__))
# 获取上层目录：dirname
project_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
# 输出的是/mnt/e/ingestion_kafka_flink/mock_data/user_actions_generator.py/../..
config_path = os.path.join(current_dir, '..', 'config', 'kafka_config.yaml')

with open(config_path, 'r') as f:
    config = yaml.safe_load(f)

kafka_config = config['kafka']
bootstrap_servers = kafka_config['bootstrap_servers']
topic = kafka_config['topic']

# 创建Kafka生产者
producer = get_producer(bootstrap_servers)

column_names = [
    'date',
    'user_id',
    'session_id',
    'page_id',
    'action_time',
    'search_keyword',
    'click_category_id',
    'click_product_id',
    'order_category_ids',
    'order_product_ids',
    'pay_category_ids',
    'pay_product_ids',
    'city_id'
]

#read fake data with pandas csv
user_visit_action_df = pd.read_csv(os.path.join(project_root, "test_data", "user_visit_action.txt"), sep="\t", header=None, names=column_names)

# user_visit_action_df.head()

# user_visit_action_df.info()

each_action_dfs = user_visit_action_df.head(10)

df = pd.DataFrame()

#transform date to timestamp
df['date'] = pd.to_datetime(each_action_dfs['date'], errors='coerce').astype('int64') // 10**6
df['action_time'] = pd.to_datetime(each_action_dfs['action_time'], errors='coerce').astype('int64') // 10**6

#transform string
str_fields = [
    'session_id', 'search_keyword', 'order_category_ids',
    'order_product_ids', 'pay_category_ids', 'pay_product_ids'
]

for field in str_fields:
    df[field] = each_action_dfs[field].astype('string')

#transform int
int_fields = ['user_id', 'page_id', 'click_category_id', 'click_product_id', 'city_id']

# 这一句的意义是 —— 到这里你已经没有 NaN了（因为填了 -1），所以就可以放心转换为 int 类型。
for field in int_fields:
    df[field] = pd.to_numeric(each_action_dfs[field], errors='coerce').fillna(-1).astype('int')

events = df.to_dict(orient='records')

print(f"Starting to send events to Kafka topic '{topic}' on {bootstrap_servers}...")
try:
    for event in events:
        send_event(producer, topic, event) # 使用辅助函数发送
        time.sleep(random.uniform(0.5, 1.5)) # 随机等待
except KeyboardInterrupt:
    print("\nStopping producer...")
finally:
    producer.close() # 关闭生产者连接
    print("Producer closed.")