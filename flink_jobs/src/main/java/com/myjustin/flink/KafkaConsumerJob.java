package com.myjustin.flink;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.TableResult;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.flink.table.api.StatementSet;
// Add: DataStream API related dependencies
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;
import org.apache.flink.table.api.Schema;
import org.apache.flink.table.api.DataTypes;
import org.apache.flink.table.api.Table;
import org.apache.flink.types.Row;

// Add: Jackson JSON Parsing
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

// Add: AWS SDK classes for reading config from Systems Manager Parameter Store
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KafkaConsumerJob {
    private static final Logger LOG = LoggerFactory.getLogger(KafkaConsumerJob.class);

    public static void main(String[] args) throws Exception {
        // Fetch project context from environment variables (Injected by Terraform/ECS)
        String projectName = System.getenv("PROJECT_NAME");
        String environment = System.getenv("ENVIRONMENT");
        String awsRegion = System.getenv().getOrDefault("AWS_REGION", "us-east-1");

        if (projectName == null || environment == null) {
            LOG.error("CRITICAL ERROR: Environment variables 'PROJECT_NAME' and 'ENVIRONMENT' are not set. Application terminating.");
            throw new RuntimeException("Missing mandatory configuration variables: PROJECT_NAME, ENVIRONMENT");
        }

        LOG.info("Starting Flink Job. Project: {}, Environment: {}, Region: {}", projectName, environment, awsRegion);

        // Initialize AWS SSM Client for fetching config
        SsmClient ssmClient = SsmClient.builder()
                                      .region(Region.of(awsRegion))
                                      .build();

        // Fetch Kafka Bootstrap Brokers address from SSM Parameter Store
        String kafkaBootstrapServers = getParameter(ssmClient, String.format("/%s/%s/kafka/bootstrap_brokers_sasl_iam", projectName, environment));
        String kafkaTopicName = getParameter(ssmClient, String.format("/%s/%s/kafka/topic_name", projectName, environment));
        String flinkOutputS3Bucket = getParameter(ssmClient, String.format("/%s/%s/s3/flink_output_bucket", projectName, environment));
        String flinkDlqS3Path = getParameter(ssmClient, String.format("/%s/%s/s3/flink_dlq_path", projectName, environment));
        String kafkaConsumerGroupId = getParameter(ssmClient, String.format("/%s/%s/kafka/consumer_group_id", projectName, environment));

        LOG.info("=========================================== SSM Parameters Loaded ====================================================");
        LOG.info("kafkaBootstrapServers : {}", kafkaBootstrapServers);
        LOG.info("kafkaTopicName : {}", kafkaTopicName);
        LOG.info("flinkOutputS3Bucket : {}", flinkOutputS3Bucket);
        LOG.info("kafkaConsumerGroupId : {}", kafkaConsumerGroupId);
        LOG.info("======================================================================================================================");

        // 1. Create Stream Execution Environment
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        
        // Enable Checkpointing, triggered every 60 seconds
        env.enableCheckpointing(60000);

        // Set consistency semantics: EXACTLY_ONCE
        env.getCheckpointConfig().setCheckpointingMode(CheckpointingMode.EXACTLY_ONCE);
        
        // Dynamic S3 bucket for checkpoint storage
        // env.getCheckpointConfig().setCheckpointStorage("s3://" + flinkOutputS3Bucket + "/checkpoints/");

        // 2. Create Table Environment
        EnvironmentSettings settings = EnvironmentSettings.newInstance().inStreamingMode().build();
        StreamTableEnvironment tEnv = StreamTableEnvironment.create(env, settings);

        // ==========================================================================================
        // Refactor Start: Use DataStream API + Side Output to implement DLQ logic
        // ==========================================================================================

        // 3.1 Define Side Output Tag for DLQ
        final OutputTag<Row> dlqTag = new OutputTag<Row>("dlq-output"){};

        // 3.2 Create Kafka Source using DataStream API (Read raw string)
        KafkaSource<String> kafkaSource = KafkaSource.<String>builder()
                .setBootstrapServers(kafkaBootstrapServers)
                .setTopics(kafkaTopicName)
                .setGroupId(kafkaConsumerGroupId)
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new SimpleStringSchema()) // Read as plain text
                // Configure IAM Authentication
                .setProperty("security.protocol", "SASL_SSL")
                .setProperty("sasl.mechanism", "AWS_MSK_IAM")
                .setProperty("sasl.jaas.config", "software.amazon.msk.auth.iam.IAMLoginModule required;")
                .setProperty("sasl.client.callback.handler.class", "software.amazon.msk.auth.iam.IAMClientCallbackHandler")
                .build();

        DataStream<String> rawStream = env.fromSource(kafkaSource, WatermarkStrategy.noWatermarks(), "Kafka Raw Source");

        // 3.3 Use ProcessFunction for manual parsing and splitting
        SingleOutputStreamOperator<Row> processedStream = rawStream.process(new ProcessFunction<String, Row>() {
            private transient ObjectMapper objectMapper;

            @Override
            public void open(Configuration parameters) throws Exception {
                objectMapper = new ObjectMapper();
            }

            @Override
            public void processElement(String value, Context ctx, Collector<Row> out) throws Exception {
                try {
                    // Try parsing JSON
                    JsonNode node = objectMapper.readTree(value);

                    // Simple validation: e.g., user_id must exist
                    if (!node.has("user_id") || node.get("user_id").isNull()) {
                        throw new RuntimeException("Missing or null user_id");
                    }

                    // Assemble normal data Row (Order must match MainDataSchema below)
                    Row row = Row.of(
                        node.get("user_id").asInt(),
                        node.has("session_id") ? node.get("session_id").asText() : null,
                        node.has("page_id") ? node.get("page_id").asInt() : null,
                        node.has("action_time") ? node.get("action_time").asLong() : null,
                        node.has("search_keyword") ? node.get("search_keyword").asText() : null,
                        node.has("click_category_id") ? node.get("click_category_id").asInt() : null,
                        node.has("click_product_id") ? node.get("click_product_id").asInt() : null,
                        node.has("order_category_ids") ? node.get("order_category_ids").asText() : null,
                        node.has("order_product_ids") ? node.get("order_product_ids").asText() : null,
                        node.has("pay_category_ids") ? node.get("pay_category_ids").asText() : null,
                        node.has("pay_product_ids") ? node.get("pay_product_ids").asText() : null,
                        node.has("city_id") ? node.get("city_id").asInt() : null
                    );
                    
                    // Send to Main Output
                    out.collect(row);

                } catch (Exception e) {
                    // Send to Side Output (DLQ)
                    // Row structure: raw_message, error_message, processing_time
                    Row errorRow = Row.of(
                        value, // Raw message
                        e.getMessage(), // Error message
                        java.time.LocalDateTime.now() // Current processing time
                    );
                    ctx.output(dlqTag, errorRow);
                }
            }
        });

        // 3.4 Get Side Output Stream (DLQ Stream)
        DataStream<Row> dlqStream = processedStream.getSideOutput(dlqTag);

        // 3.5 Convert Main Stream to Table, and register as temporary view
        Schema mainDataSchema = Schema.newBuilder()
                .column("user_id", DataTypes.INT())
                .column("session_id", DataTypes.STRING())
                .column("page_id", DataTypes.INT())
                .column("action_time", DataTypes.BIGINT())
                .column("search_keyword", DataTypes.STRING())
                .column("click_category_id", DataTypes.INT())
                .column("click_product_id", DataTypes.INT())
                .column("order_category_ids", DataTypes.STRING())
                .column("order_product_ids", DataTypes.STRING())
                .column("pay_category_ids", DataTypes.STRING())
                .column("pay_product_ids", DataTypes.STRING())
                .column("city_id", DataTypes.INT())
                .build();
        
        Table mainTable = tEnv.fromDataStream(processedStream, mainDataSchema);
        tEnv.createTemporaryView("MainDataView", mainTable);

        // 3.6 Convert Side Output Stream to Table, and register as temporary view
        Schema dlqDataSchema = Schema.newBuilder()
                .column("raw_message", DataTypes.STRING())
                .column("error_message", DataTypes.STRING())
                .column("processing_time", DataTypes.TIMESTAMP(3))
                .build();
        
        Table dlqTable = tEnv.fromDataStream(dlqStream, dlqDataSchema);
        tEnv.createTemporaryView("DlqDataView", dlqTable);

        // 3.7 (Old KafkaSource DDL commented out)
        // 3.7 (注释掉旧的 KafkaSource DDL)
        /*
        tEnv.executeSql(
                "CREATE TABLE KafkaSource (" +
                        "    `date` BIGINT," +
                        "    user_id INT," +
                        "    session_id STRING," +
                        "    page_id INT," +
                        "    action_time BIGINT," +
                        "    search_keyword STRING," +
                        "    click_category_id INT," +
                        "    click_product_id INT," +
                        "    order_category_ids STRING," +
                        "    order_product_ids STRING," +
                        "    pay_category_ids STRING," +
                        "    pay_product_ids STRING," +
                        "    city_id INT" +
                        ") WITH (" +
                        // "    'connector' = 'kafka'," +
                        // "    'topic' = 'user_behavior'," +
                        // // "    'properties.bootstrap.servers' = 'kafka:9093'," +
                        // "    'properties.group.id' = 'flink_consumer_group'," +
                        // "    'scan.startup.mode' = 'latest-offset'," +
                        // "    'format' = 'json'," +
                        // // produce set
                        // "    'properties.bootstrap.servers' = '" + bootstrapServers + "'" + // 直接将地址写入
                                "'connector' = 'kafka'," +
                                // 注释掉旧的硬编码 topic，改为从 SSM 获取的动态 topic 名称
                                // "'topic' = 'user_behavior_01'," +
                                "'topic' = '" + kafkaTopicName + "'," + // 新增：使用动态获取的 Kafka Topic 名称
                                // 注释掉旧的硬编码 bootstrap servers，改为从 SSM 获取的动态地址
                                // "'properties.bootstrap.servers' = 'b-1.flinkstagingkafkaclus.oj6v2z.c23.kafka.us-east-1.amazonaws.com:9098,b-2.flinkstagingkafkaclus.oj6v2z.c23.kafka.us-east-1.amazonaws.com:9098'," +
                                "'properties.bootstrap.servers' = '" + kafkaBootstrapServers + "'," + // 新增：使用动态获取的 Kafka Bootstrap Servers
                                "'properties.security.protocol' = 'SASL_SSL'," +
                                "'properties.sasl.mechanism' = 'AWS_MSK_IAM'," +
                                "'properties.sasl.jaas.config' = 'software.amazon.msk.auth.iam.IAMLoginModule required;'," +
                                "'properties.sasl.client.callback.handler.class' = 'software.amazon.msk.auth.iam.IAMClientCallbackHandler'," +
                                // 注释掉旧的硬编码 consumer group id，改为从 SSM 获取的动态 ID
                                // "'properties.group.id' = 'flink_consumer_group'," +
                                "'properties.group.id' = '" + kafkaConsumerGroupId + "'," + // 新增：使用动态获取的 Kafka Consumer Group ID
                                "'scan.startup.mode' = 'latest-offset'," +
                                "'format' = 'json'" +
                        ")"
        );
        */

        // 4. Define Local Filesystem Sink Table (DDL) - Unchanged
        // 4. 定义本地文件系统 Sink 表 (DDL) - 保持不变
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
                        "    'path'='s3://" + flinkOutputS3Bucket + "/user_action/'," + // Use dynamic S3 bucket
                        "    'sink.rolling-policy.file-size' = '100MB'," +
                        "    'sink.rolling-policy.rollover-interval' = '1 min'," + 
                        "    'sink.partition-commit.policy.kind' = 'success-file'," +
                        "    'sink.partition-commit.trigger' = 'process-time'" +
                        ")"
        );
        LOG.info("CREATE TABLE S3Sink executed.");

        // 6. Define DLQ Sink Table (DDL)
        tEnv.executeSql(
                "CREATE TABLE DLQSink (" +
                        "    raw_message STRING," +
                        "    error_message STRING," +
                        "    processing_time TIMESTAMP(3)" +
                        ") WITH (" +
                        "    'connector' = 'filesystem'," +
                        "    'format' = 'json'," +
                        "    'path' = '" + flinkDlqS3Path + "'," +
                        "    'sink.rolling-policy.file-size' = '100MB'," +
                        "    'sink.rolling-policy.rollover-interval' = '1 min'" +
                        ")"
        );
        LOG.info("CREATE TABLE DLQSink executed.");

        // 7. Define Core ETL Logic (DML)
        StatementSet statementSet = tEnv.createStatementSet();

        // Normal Data Write: Read from MainDataView
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

        // Error Data Write: Read from DlqDataView
        statementSet.addInsertSql(
                "INSERT INTO DLQSink " +
                        "SELECT " +
                        "    raw_message, " +
                        "    error_message, " +
                        "    processing_time " +
                        "FROM DlqDataView"
        );

        LOG.info("StatementSet defined. Submitting Flink Job...");
        TableResult result = statementSet.execute();
        result.await();
    }

    /**
     * Add: Helper method to fetch parameter value from AWS Systems Manager Parameter Store.
     *
     * @param ssmClient SsmClient instance
     * @param parameterName Name of the parameter to fetch
     * @return Parameter value
     * @throws RuntimeException If parameter fetching fails
     */
    private static String getParameter(SsmClient ssmClient, String parameterName) {
        try {
            GetParameterRequest request = GetParameterRequest.builder()
                                                            .name(parameterName)
                                                            .withDecryption(true) // Decrypt if parameter is SecureString
                                                            .build();
            GetParameterResponse response = ssmClient.getParameter(request);
            return response.parameter().value();
        } catch (Exception e) {
            LOG.error("Error getting parameter: {}. Error: {}", parameterName, e.getMessage());
            throw new RuntimeException("Failed to get parameter: " + parameterName, e);
        }
    }
}