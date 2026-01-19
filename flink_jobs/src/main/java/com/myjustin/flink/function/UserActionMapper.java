package com.myjustin.flink.function;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myjustin.flink.model.DlqRecord;
import com.myjustin.flink.model.UserAction;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;

import java.time.LocalDateTime;

/**
 * Handles the transformation from raw JSON string to UserAction POJO.
 * Invalid records are sent to a Side Output (DLQ).
 */
public class UserActionMapper extends ProcessFunction<String, UserAction> {
    
    // OutputTag for Side Output needs to be accessible by the main job
    public static final OutputTag<DlqRecord> DLQ_TAG = new OutputTag<DlqRecord>("dlq-output"){};

    private transient ObjectMapper objectMapper;

    @Override
    public void open(Configuration parameters) throws Exception {
        objectMapper = new ObjectMapper();
    }

    @Override
    public void processElement(String value, Context ctx, Collector<UserAction> out) throws Exception {
        try {
            JsonNode node = objectMapper.readTree(value);

            // Validation logic
            if (!node.has("user_id") || node.get("user_id").isNull()) {
                throw new RuntimeException("Missing or null user_id");
            }

            // Mapping logic
            UserAction action = new UserAction();
            action.user_id = node.get("user_id").asInt();
            action.session_id = node.has("session_id") ? node.get("session_id").asText() : null;
            action.page_id = node.has("page_id") ? node.get("page_id").asInt() : null;
            action.action_time = node.has("action_time") ? node.get("action_time").asLong() : null;
            action.search_keyword = node.has("search_keyword") ? node.get("search_keyword").asText() : null;
            action.click_category_id = node.has("click_category_id") ? node.get("click_category_id").asInt() : null;
            action.click_product_id = node.has("click_product_id") ? node.get("click_product_id").asInt() : null;
            action.order_category_ids = node.has("order_category_ids") ? node.get("order_category_ids").asText() : null;
            action.order_product_ids = node.has("order_product_ids") ? node.get("order_product_ids").asText() : null;
            action.pay_category_ids = node.has("pay_category_ids") ? node.get("pay_category_ids").asText() : null;
            action.pay_product_ids = node.has("pay_product_ids") ? node.get("pay_product_ids").asText() : null;
            action.city_id = node.has("city_id") ? node.get("city_id").asInt() : null;

            out.collect(action);

        } catch (Exception e) {
            // Side Output for DLQ
            ctx.output(DLQ_TAG, new DlqRecord(value, e.getMessage(), LocalDateTime.now()));
        }
    }
}
