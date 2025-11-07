import json
import socket
import os
import boto3
from confluent_kafka import Producer
from aws_msk_iam_sasl_signer import MSKAuthTokenProvider
import certifi   # 用作 ssl CA 的 fallback（可选但建议

def get_bootstrap_servers_from_ssm(region_name='us-east-1'):
    """
    Fetches the Kafka bootstrap servers from AWS SSM Parameter Store.
    The environment (e.g., 'dev', 'prod') is determined by the 'ENV' environment variable.
    """
    # The 'ENV' environment variable should match the 'var.environment' in your Terraform setup.
    environment = os.environ.get('ENV', 'dev') # Default to 'dev' if not set
    parameter_name = f"/data-platform/{environment}/kafka/bootstrap_brokers_private"
    print(f"Fetching bootstrap servers from SSM parameter: {parameter_name}")
    try:
        ssm_client = boto3.client('ssm', region_name=region_name)
        response = ssm_client.get_parameter(Name=parameter_name)
        bootstrap_servers_value = response['Parameter']['Value']
        print(f"Successfully fetched bootstrap servers: {bootstrap_servers_value}")
        return bootstrap_servers_value
    except Exception as e:
        print(f"FATAL: Could not fetch bootstrap servers from SSM Parameter Store. Please check IAM permissions and if the parameter '{parameter_name}' exists. Error: {e}")
        raise

class MSKTokenProvider:
    def __init__(self, region):
        self.region = region

    def token(self):
        # generate_auth_token 返回 (token, expiry_ms)
        auth_token, expiry_ms = MSKAuthTokenProvider.generate_auth_token(self.region)
        # confluent expects expiry as seconds since epoch (float)
        expiry_seconds = expiry_ms / 1000.0
        return auth_token, expiry_seconds

def get_producer(bootstrap_servers, security_config=None):
    conf = {
        'bootstrap.servers': bootstrap_servers,
        'client.id': f'msk-iam-producer-{socket.gethostname()}',
        'socket.timeout.ms': 30000,
        'api.version.request.timeout.ms': 30000,
        'debug': 'security,broker,protocol'
    }

    if security_config and security_config.get('mechanism') == 'AWS_MSK_IAM':
        token_provider = MSKTokenProvider(security_config['region'])

        print(f"token_provider is {token_provider}")

        # confluent 的 oauth_cb 接受一个可选的 config_str 参数
        def oauth_cb(config_str=None):
            token, expiry_seconds = token_provider.token()
            # 返回 (token_str, expiry_time_in_seconds_since_epoch)
            return token, float(expiry_seconds)

# AWS_MSK_IAM 是你在代码或者配置里用来标识“用 IAM 认证”的一个标识符，属于逻辑层的约定。
# OAUTHBEARER 是 Kafka SASL 协议里规定的机制名，是 Kafka 客户端实际使用的认证协议名称。
# AWS MSK 的 IAM 认证方案，是用 OAuth 2.0 Bearer Token 的方式实现的。
# 所以 Kafka 客户端需要告诉 Kafka Broker：我用的是 OAUTHBEARER 机制。
# 这就是为什么生产者配置 sasl.mechanisms 赋值为 "OAUTHBEARER"。
# 本地的 Kafka（或者你本地用的 MSK 客户端配置文件里）写的是 AWS_MSK_IAM，那应该是你配置文件里用来触发 IAM 认证模块加载的“伪机制名”，但 Kafka Broker 只识别标准 SASL 机制名。
# 实际 Kafka Broker 只识别标准机制：PLAIN, SCRAM-SHA-512, OAUTHBEARER 等。
# AWS MSK IAM 认证是基于 OAUTHBEARER 的扩展，所以你客户端要用标准名 "OAUTHBEARER"。
# 你本地配置用 AWS_MSK_IAM 其实是启动相关回调处理的标志，不是 Kafka 机制本身。

        conf.update({
            'security.protocol': 'SASL_SSL',
            'sasl.mechanisms': 'OAUTHBEARER',     # 要用标准名 OAUTHBEARER
            'oauth_cb': oauth_cb,
            'ssl.ca.location': security_config.get('ssl_cafile') or certifi.where()
        })

    print("--- Creating Kafka Producer with the following configuration: ---")
    print(json.dumps({k: v for k, v in conf.items() if k != 'oauth_cb'}, indent=2))
    print("-------------------------------------------------------------")

    return Producer(conf)

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
    """
    使用 confluent-kafka 生产者发送单个事件。
    这个函数现在是异步的，但我们用 poll(0) 来触发回调。
    """
    
    # 投递报告回调函数
    def delivery_report(err, msg):
        """ 消息发送后被调用。 """
        if err is not None:
            print(f"Failed to deliver message: {err}")
        else:
            print(f"Message delivered to topic '{msg.topic()}' in partition [{msg.partition()}]")

    try:
        # 打印要发送的内容
        print("📤 Sending event:")
        print(json.dumps(event, indent=2))  # 打印更清晰
        
        # 将事件序列化为 JSON 字符串
        value = json.dumps(event).encode('utf-8')
        
        # 生产消息，这是一个非阻塞的操作
        producer.produce(topic, value=value, callback=delivery_report)
        
        # poll(0) 会触发所有等待的回调函数（比如我们的 delivery_report），
        # 但不会阻塞。这对于循环发送非常高效。
        producer.poll(0)
        
    except Exception as e:
        print(f"Error producing message: {e}")