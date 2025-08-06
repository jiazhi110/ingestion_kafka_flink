from kafka import KafkaProducer
import json
from aws_msk_iam_sasl_signer import MSKAuthTokenProvider

class MSKTokenProvider(MSKAuthTokenProvider):
    def token(self):
        token, _ = self.generate_auth_token('us-east-1') # <-- 替换成您的 AWS 区域
        return token

def get_producer(bootstrap_servers, security_config=None):
#     """
#     创建一个 Kafka 生产者，支持 IAM 认证。
#     """
    if security_config and security_config.get('mechanism') == 'AWS_MSK_IAM':
        producer = KafkaProducer(
            bootstrap_servers=bootstrap_servers.split(','),
            value_serializer=lambda v: json.dumps(v).encode('utf-8'),
            security_protocol='SASL_SSL',
            sasl_mechanism='OAUTHBEARER', # IAM 使用 OAUTHBEARER 机制
            sasl_oauth_token_provider=MSKTokenProvider()
        )
    else:
        # 否则，使用普通的 plaintext 连接
        producer = KafkaProducer(
            bootstrap_servers=bootstrap_servers.split(','),
            value_serializer=lambda v: json.dumps(v).encode('utf-8')
        )
    return producer

# def get_producer(bootstrap_servers, security_config=None):
#     """
#     创建一个 Kafka 生产者，支持 SASL/SCRAM 认证。
#     """
#     if security_config:
#         # 如果有安全配置，则使用 SASL/SCRAM 连接
#         producer = KafkaProducer(
#             bootstrap_servers=bootstrap_servers.split(','), # 支持多个服务器
#             value_serializer=lambda v: json.dumps(v).encode('utf-8'),
#             security_protocol=security_config['protocol'],
#             sasl_mechanism=security_config['mechanism'],
#             sasl_plain_username=security_config['username'],
#             sasl_plain_password=security_config['password'],
#             ssl_cafile=security_config['cafile']
#         )
#     else:
#         # 否则，使用普通的 plaintext 连接
#         producer = KafkaProducer(
#             bootstrap_servers=bootstrap_servers.split(','),
#             value_serializer=lambda v: json.dumps(v).encode('utf-8')
#         )
#     return producer

def send_event(producer, topic, event):
    # 发送事件到Kafka，确保发送成功
    producer.send(topic, event).add_callback(lambda _: print(f"Sent: {event}")) \
                               .add_errback(lambda ex: print(f"Failed to send: {event} with error: {ex}"))
    producer.flush() # 立即发送所有挂起的消息
    print(f"Successfully sent event for user_id: {event.get('user_id')}")