import json
import socket
import os
import boto3
import logging
from confluent_kafka import Producer
from aws_msk_iam_sasl_signer import MSKAuthTokenProvider
import certifi

# Configure Logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

def get_ssm_parameter(parameter_path):
    """
    Helper function: Fetch a single parameter from SSM.
    """
    aws_region = os.environ.get('AWS_REGION', 'us-east-1')
    try:
        ssm_client = boto3.client('ssm', region_name=aws_region)
        response = ssm_client.get_parameter(Name=parameter_path)
        return response['Parameter']['Value']
    except Exception as e:
        logger.error(f"Error fetching SSM parameter {parameter_path}: {e}")
        return None

def get_kafka_config_from_ssm():
    """
    Fetch Kafka configuration (Brokers and Topic) from SSM at once.
    Requires environment variables PROJECT_NAME and ENVIRONMENT.
    """
    project_name = os.environ.get('PROJECT_NAME')
    environment = os.environ.get('ENVIRONMENT')
    
    if not project_name or not environment:
        logger.error("CRITICAL ERROR: Environment variables 'PROJECT_NAME' and 'ENVIRONMENT' must be set.")
        raise EnvironmentError("Missing required configuration environment variables.")

    # Get Brokers
    brokers_path = f"/{project_name}/{environment}/kafka/bootstrap_brokers_sasl_iam"
    # Get Topic
    topic_path = f"/{project_name}/{environment}/kafka/topic_name"
    
    brokers = get_ssm_parameter(brokers_path)
    topic = get_ssm_parameter(topic_path)
    
    return brokers, topic

def get_bootstrap_servers_from_ssm():
    """
    (Legacy Compatibility) Fetch Kafka bootstrap servers.
    """
    brokers, _ = get_kafka_config_from_ssm()
    return brokers

class MSKTokenProvider:
    def __init__(self, region):
        self.region = region

    def token(self):
        # generate_auth_token returns (token, expiry_ms)
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
        'acks': 'all',             # Critical: Ensure all replicas acknowledge receipt to prevent data loss
        'retries': 5,              # Automatic retries
        'retry.backoff.ms': 500,   # Retry backoff
        'debug': 'security,broker,protocol'
    }

    # If security_config is missing but we are on AWS (implied by env var), default to IAM
    if not security_config and os.environ.get('AWS_REGION'):
        security_config = {
            'mechanism': 'AWS_MSK_IAM',
            'region': os.environ.get('AWS_REGION', 'us-east-1')
        }

    if security_config and security_config.get('mechanism') == 'AWS_MSK_IAM':
        token_provider = MSKTokenProvider(security_config['region'])

# AWS_MSK_IAM is the identifier we use in code/config to signal "Use IAM Auth", a logical convention.
# OAUTHBEARER is the actual SASL mechanism name defined in Kafka protocol that the client uses.
# AWS MSK's IAM authentication is implemented using OAuth 2.0 Bearer Token.
# Therefore, the Kafka client needs to tell the Broker: "I am using OAUTHBEARER mechanism".
# This is why the producer config `sasl.mechanisms` is set to "OAUTHBEARER".
# The `AWS_MSK_IAM` string in your local config is just a flag to trigger the loading of the IAM auth module/callback, NOT the Kafka mechanism itself.
# Actual Kafka Brokers only recognize standard mechanisms: PLAIN, SCRAM-SHA-512, OAUTHBEARER, etc.

        def oauth_cb(config_str=None):
            token, expiry_seconds = token_provider.token()
            return token, float(expiry_seconds)

        conf.update({
            'security.protocol': 'SASL_SSL',
            'sasl.mechanisms': 'OAUTHBEARER',     # Use standard name OAUTHBEARER
            'oauth_cb': oauth_cb,
            'ssl.ca.location': security_config.get('ssl_cafile') or certifi.where()
        })

    logger.info("--- Creating Kafka Producer with configuration ---")
    return Producer(conf)

def send_event(producer, topic, event):
    """
    Send a single event using confluent-kafka producer.
    This function is asynchronous, but we use poll(0) to trigger callbacks.
    """
    
    # Delivery report callback
    def delivery_report(err, msg):
        """ Called after message delivery. """
        if err is not None:
            logger.error(f"Failed to deliver message: {err}")
        else:
            logger.info(f"Message delivered to {msg.topic()} [{msg.partition()}] at offset {msg.offset()}")

    try:
        # Serialize event to JSON string
        value = json.dumps(event).encode('utf-8')
        
        # Produce message (Non-blocking)
        producer.produce(topic, value=value, callback=delivery_report)
        
        # poll(0) triggers any waiting callbacks (like our delivery_report),
        # but does not block. This is highly efficient for loop sending.
        producer.poll(0)
    except Exception as e:
        logger.error(f"Error producing message: {e}")