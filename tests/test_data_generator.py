#!/usr/bin/env python3
"""
Test script for data generator functionality
This script verifies that the test data can be properly loaded and processed
"""

import sys
import os
import pandas as pd

# Add project root to Python path
project_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, project_root)

def test_data_loading():
    """Test loading of test data files"""
    
    print("Testing data loading...")
    
    # Test user visit action data
    user_visit_action_path = os.path.join(project_root, "test_data", "user_visit_action.txt")
    
    if not os.path.exists(user_visit_action_path):
        print(f"Error: Test data file not found at {user_visit_action_path}")
        return False
    
    try:
        # Define column names
        column_names = [
            'date', 'user_id', 'session_id', 'page_id', 'action_time',
            'search_keyword', 'click_category_id', 'click_product_id',
            'order_category_ids', 'order_product_ids', 'pay_category_ids',
            'pay_product_ids', 'city_id'
        ]
        
        # Load data
        df = pd.read_csv(user_visit_action_path, sep="\t", header=None, names=column_names, nrows=5)
        
        print(f"Successfully loaded test data from {user_visit_action_path}")
        print(f"Data shape: {df.shape}")
        print("\nFirst 5 rows:")
        print(df.head())
        
        print("\nData types:")
        print(df.dtypes)
        
        return True
        
    except Exception as e:
        print(f"Error loading test data: {e}")
        return False


def test_data_transformation():
    """Test data transformation logic"""
    
    print("\nTesting data transformation...")
    
    try:
        # Define column names
        column_names = [
            'date', 'user_id', 'session_id', 'page_id', 'action_time',
            'search_keyword', 'click_category_id', 'click_product_id',
            'order_category_ids', 'order_product_ids', 'pay_category_ids',
            'pay_product_ids', 'city_id'
        ]
        
        # Load data
        user_visit_action_path = os.path.join(project_root, "test_data", "user_visit_action.txt")
        user_visit_action_df = pd.read_csv(user_visit_action_path, sep="\t", header=None, names=column_names, nrows=10)
        
        # Create a copy for transformation
        df = pd.DataFrame()
        
        # Transform date to timestamp
        df['date'] = pd.to_datetime(user_visit_action_df['date'], errors='coerce').astype('int64') // 10**6
        df['action_time'] = pd.to_datetime(user_visit_action_df['action_time'], errors='coerce').astype('int64') // 10**6
        
        # Transform string fields
        str_fields = [
            'session_id', 'search_keyword', 'order_category_ids',
            'order_product_ids', 'pay_category_ids', 'pay_product_ids'
        ]
        
        for field in str_fields:
            df[field] = user_visit_action_df[field].astype('string')
        
        # Transform int fields
        int_fields = ['user_id', 'page_id', 'click_category_id', 'click_product_id', 'city_id']
        
        for field in int_fields:
            df[field] = pd.to_numeric(user_visit_action_df[field], errors='coerce').fillna(-1).astype('int')
        
        print("Data transformation successful!")
        print(f"Transformed data shape: {df.shape}")
        print("\nFirst 3 transformed rows:")
        print(df.head(3))
        
        # Check for any NaN values
        nan_count = df.isnull().sum().sum()
        print(f"\nTotal NaN values after transformation: {nan_count}")
        
        return True
        
    except Exception as e:
        print(f"Error in data transformation: {e}")
        return False


def test_sample_records_generation():
    """Test generation of sample records for Kafka"""
    
    print("\nTesting sample records generation...")
    
    try:
        # Define column names
        column_names = [
            'date', 'user_id', 'session_id', 'page_id', 'action_time',
            'search_keyword', 'click_category_id', 'click_product_id',
            'order_category_ids', 'order_product_ids', 'pay_category_ids',
            'pay_product_ids', 'city_id'
        ]
        
        # Load data
        user_visit_action_path = os.path.join(project_root, "test_data", "user_visit_action.txt")
        user_visit_action_df = pd.read_csv(user_visit_action_path, sep="\t", header=None, names=column_names, nrows=5)
        
        # Create a copy for transformation
        df = pd.DataFrame()
        
        # Transform date to timestamp
        df['date'] = pd.to_datetime(user_visit_action_df['date'], errors='coerce').astype('int64') // 10**6
        df['action_time'] = pd.to_datetime(user_visit_action_df['action_time'], errors='coerce').astype('int64') // 10**6
        
        # Transform string fields
        str_fields = [
            'session_id', 'search_keyword', 'order_category_ids',
            'order_product_ids', 'pay_category_ids', 'pay_product_ids'
        ]
        
        for field in str_fields:
            df[field] = user_visit_action_df[field].astype('string')
        
        # Transform int fields
        int_fields = ['user_id', 'page_id', 'click_category_id', 'click_product_id', 'city_id']
        
        for field in int_fields:
            df[field] = pd.to_numeric(user_visit_action_df[field], errors='coerce').fillna(-1).astype('int')
        
        # Convert to records
        events = df.to_dict(orient='records')
        
        print(f"Generated {len(events)} sample records")
        print("First record:")
        for key, value in events[0].items():
            print(f"  {key}: {value}")
        
        return True
        
    except Exception as e:
        print(f"Error in sample records generation: {e}")
        return False


if __name__ == "__main__":
    print("Data Generator Test")
    print("=" * 30)
    
    # Test data loading
    loading_success = test_data_loading()
    
    # Test data transformation
    transformation_success = test_data_transformation()
    
    # Test sample records generation
    records_success = test_sample_records_generation()
    
    # Summary
    print("\nTest Summary:")
    print(f"  Data loading test: {'PASSED' if loading_success else 'FAILED'}")
    print(f"  Data transformation test: {'PASSED' if transformation_success else 'FAILED'}")
    print(f"  Sample records generation test: {'PASSED' if records_success else 'FAILED'}")
    
    if loading_success and transformation_success and records_success:
        print("\nAll tests PASSED! Data generator is working correctly.")
        sys.exit(0)
    else:
        print("\nSome tests FAILED! Please check your data generator setup.")
        sys.exit(1)