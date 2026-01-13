import json, time, random
import os
import pandas as pd
import logging
from mykafka.kafka_utils import get_producer, send_event, get_kafka_config_from_ssm 

# Configure logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

# --- Core Data Processing Logic (Extracted as a function for easier unit testing) ---
def process_user_actions(raw_df):
    """
    Clean and transform raw user action data.
    
    Main logic:
    1. Timestamp conversion (Date/Time -> Timestamp ms)
    2. Null value handling (NaN -> -1)
    3. Type casting (String/Int)
    """
    processed_df = pd.DataFrame()

    # 1. Transform date to timestamp (ms)
    # Use errors='coerce' to handle invalid date formats
    processed_df['date'] = pd.to_datetime(raw_df['date'], errors='coerce').astype('int64') // 10**6
    processed_df['action_time'] = pd.to_datetime(raw_df['action_time'], errors='coerce').astype('int64') // 10**6

    # 2. Transform string fields
    str_fields = [
        'session_id', 'search_keyword', 'order_category_ids',
        'order_product_ids', 'pay_category_ids', 'pay_product_ids'
    ]
    for field in str_fields:
        processed_df[field] = raw_df[field].astype('string')

    # 3. Transform int fields
    int_fields = ['user_id', 'page_id', 'click_category_id', 'click_product_id', 'city_id']
    for field in int_fields:
        processed_df[field] = pd.to_numeric(raw_df[field], errors='coerce').fillna(-1).astype('int')
        
    return processed_df

# --- Main Execution Logic ---
if __name__ == "__main__":
    # --- Configuration Loading (Cloud-Native / Hybrid Mode) ---
    # Architecture Explanation:
    # This project follows Cloud-Native best practices, configuration is fully managed by AWS SSM Parameter Store.
    # For local development, it accesses SSM of Dev environment by configuring AWS credentials (Shared Credentials).

    current_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

    # Get configuration from SSM (Single Source of Truth)
    try:
        bootstrap_servers, topic = get_kafka_config_from_ssm()
        if not bootstrap_servers or not topic:
            raise ValueError("Failed to retrieve config from SSM")
    except Exception as e:
        logger.error("FATAL: Unable to load configuration from AWS SSM. Please check your AWS credentials and Region.")
        raise e

    logger.info(f"Successfully loaded config from SSM. Topic: {topic}")

    # Default to use AWS MSK IAM authentication
    security_config = {
        'mechanism': 'AWS_MSK_IAM',
        'region': os.environ.get('AWS_REGION', 'us-east-1')
    }

    # Create Kafka Producer
    producer = get_producer(bootstrap_servers, security_config)

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

    # read fake data with pandas csv
    data_path = os.path.join(project_root, "test_data", "user_visit_action.txt")
    logger.info(f"Loading test data from: {data_path}")
    
    try:
        user_visit_action_df = pd.read_csv(data_path, sep="\t", header=None, names=column_names)
        
        # Call our encapsulated logic function
        logger.info("Processing data...")
        df = process_user_actions(user_visit_action_df)

        # Convert to list of dictionaries
        events = df.to_dict(orient='records')

        logger.info(f"Starting to send {len(events)} events to Kafka topic '{topic}' on {bootstrap_servers}...")
        
        for event in events:
            send_event(producer, topic, event) # Use helper function to send
            time.sleep(random.uniform(0.5, 1.5)) # Random wait

    except KeyboardInterrupt:
        logger.info("Stopping producer...")
    except Exception as e:
        logger.error(f"Unexpected error: {e}")
    finally:
        producer.close() # Close producer connection
        logger.info("Producer closed.")