package com.myjustin.flink.job;

import com.myjustin.flink.config.ConfigLoader;
import com.myjustin.flink.function.UserActionMapper;
import com.myjustin.flink.model.DlqRecord;
import com.myjustin.flink.model.UserAction;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.StatementSet;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class KafkaConsumerJob {
    private static final Logger LOG = LoggerFactory.getLogger(KafkaConsumerJob.class);

    public static void main(String[] args) throws Exception {
        // 1. Load Configuration
        Map<String, String> config = ConfigLoader.loadConfig();

        // 2. Setup Flink Environment
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.enableCheckpointing(60000);
        env.getCheckpointConfig().setCheckpointingMode(CheckpointingMode.EXACTLY_ONCE);
        
        EnvironmentSettings settings = EnvironmentSettings.newInstance().inStreamingMode().build();
        StreamTableEnvironment tEnv = StreamTableEnvironment.create(env, settings);

        // 3. Define Kafka Source
        KafkaSource<String> kafkaSource = KafkaSource.<String>builder()
                .setBootstrapServers(config.get("kafka.bootstrap.servers"))
                .setTopics(config.get("kafka.topic"))
                .setGroupId(config.get("kafka.group.id"))
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .setProperty("security.protocol", "SASL_SSL")
                .setProperty("sasl.mechanism", "AWS_MSK_IAM")
                .setProperty("sasl.jaas.config", "software.amazon.msk.auth.iam.IAMLoginModule required;")
                .setProperty("sasl.client.callback.handler.class", "software.amazon.msk.auth.iam.IAMClientCallbackHandler")
                .build();

        DataStream<String> rawStream = env.fromSource(kafkaSource, WatermarkStrategy.noWatermarks(), "Kafka Raw Source");

        // 4. Process Data (JSON -> POJO with DLQ)
        SingleOutputStreamOperator<UserAction> processedStream = rawStream.process(new UserActionMapper());
        DataStream<DlqRecord> dlqStream = processedStream.getSideOutput(UserActionMapper.DLQ_TAG);

        // 5. Register Tables (Schema automatically inferred from POJOs)
        tEnv.createTemporaryView("MainDataView", tEnv.fromDataStream(processedStream));
        tEnv.createTemporaryView("DlqDataView", tEnv.fromDataStream(dlqStream));

        // 6. Define Sinks (S3 for valid data, S3 for DLQ)
        createS3SinkTable(tEnv, config.get("s3.output.bucket"));
        createDlqSinkTable(tEnv, config.get("s3.dlq.path"));

        // 7. Execute ETL Pipeline
        StatementSet statementSet = tEnv.createStatementSet();
        
        // Write Valid Data
        statementSet.addInsertSql(
            "INSERT INTO S3Sink " +
            "SELECT " +
            "    user_id, " +
            "    session_id, " +
            "    page_id, " +
            "    action_time AS action_time_ms, " +
            "    search_keyword, " +
            "    click_category_id, " +
            "    click_product_id, " +
            "    order_category_ids, " +
            "    order_product_ids, " +
            "    pay_category_ids, " +
            "    pay_product_ids, " +
            "    city_id, " +
            "    DATE_FORMAT(FROM_UNIXTIME(action_time / 1000), 'yyyy-MM-dd') AS dt, " +
            "    DATE_FORMAT(FROM_UNIXTIME(action_time / 1000), 'HH') AS hr " +
            "FROM MainDataView"
        );

        // Write DLQ Data: Use explicit CAST for processing_time to avoid RAW type issues
        statementSet.addInsertSql(
            "INSERT INTO DLQSink " +
            "SELECT " +
            "    raw_message, " +
            "    error_message, " +
            "    CAST(processing_time AS STRING) AS processing_time " +
            "FROM DlqDataView"
        );

        LOG.info("Submitting Flink Job...");
        statementSet.execute().await();
    }

    private static void createS3SinkTable(StreamTableEnvironment tEnv, String bucket) {
        tEnv.executeSql(
            "CREATE TABLE S3Sink (" + 
            "    user_id INT," +
            "    session_id STRING," +
            "    page_id INT," +
            "    action_time_ms BIGINT," +
            "    search_keyword STRING," +
            "    click_category_id INT," +
            "    click_product_id INT," +
            "    order_category_ids STRING," +
            "    order_product_ids STRING," +
            "    pay_category_ids STRING," +
            "    pay_product_ids STRING," +
            "    city_id INT," +
            "    dt STRING," +
            "    hr STRING" +
            ") PARTITIONED BY (dt, hr) " +
            "WITH (" +
            "    'connector' = 'filesystem'," +
            "    'format' = 'parquet'," +
            "    'path'='s3://" + bucket + "/user_action/'," +
            "    'sink.rolling-policy.file-size' = '100MB'," +
            "    'sink.rolling-policy.rollover-interval' = '1 min'," + 
            "    'sink.partition-commit.policy.kind' = 'success-file'," +
            "    'sink.partition-commit.trigger' = 'process-time'" +
            ")"
        );
    }

    private static void createDlqSinkTable(StreamTableEnvironment tEnv, String path) {
        tEnv.executeSql(
            "CREATE TABLE DLQSink (" +
            "    raw_message STRING," +
            "    error_message STRING," +
            "    processing_time STRING" +
            ") WITH (" +
            "    'connector' = 'filesystem'," +
            "    'format' = 'json'," +
            "    'path' = '" + path + "'," +
            "    'sink.rolling-policy.file-size' = '100MB'," +
            "    'sink.rolling-policy.rollover-interval' = '1 min'" +
            ")"
        );
    }
}