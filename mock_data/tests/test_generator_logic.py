import pytest
import pandas as pd
import numpy as np
from mock_data.user_actions_generator import process_user_actions

def test_process_user_actions_logic():
    """
    Test core data cleaning logic:
    1. Null value filling (-1)
    2. Type conversion (Int/String)
    3. Timestamp conversion
    """
    # 1. Construct simulated raw data (contains dirty data/null values)
    raw_data = {
        'date': ['2023-01-01', '2023-01-02'],
        'action_time': ['2023-01-01 10:00:00', '2023-01-02 11:00:00'],
        'user_id': [101, 102],
        'session_id': ['sess_1', 'sess_2'],
        'page_id': [5, np.nan],  # Test null filling
        'search_keyword': ['apple', None],
        'click_category_id': [1, 2],
        'click_product_id': [10, 20],
        'order_category_ids': [None, None],
        'order_product_ids': [None, None],
        'pay_category_ids': [None, None],
        'pay_product_ids': [None, None],
        'city_id': [1, np.nan]   # Test null filling
    }
    
    raw_df = pd.DataFrame(raw_data)
    
    # 2. Execute function under test
    result_df = process_user_actions(raw_df)
    
    # 3. Assert verification
    assert len(result_df) == 2
    
    # Verify null filling logic
    assert result_df.iloc[1]['page_id'] == -1
    assert result_df.iloc[1]['city_id'] == -1
    
    # Verify normal values
    assert result_df.iloc[0]['page_id'] == 5
    
    # Verify timestamp conversion
    assert result_df.iloc[0]['action_time'] > 0
    assert isinstance(result_df.iloc[0]['action_time'], (int, np.integer))
    
    # Verify string columns
    assert result_df.iloc[0]['session_id'] == 'sess_1'

if __name__ == "__main__":
    pytest.main([__file__])
