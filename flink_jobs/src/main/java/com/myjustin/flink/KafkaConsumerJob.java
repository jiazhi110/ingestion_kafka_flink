package com.myjustin.flink;

import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.TableResult;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;

public class KafkaConsumerJob {
    public static void main(String[] args) throws Exception {
        // 1. 创建流执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
//        StreamExecutionEnvironment env = StreamExecutionEnvironment.createRemoteEnvironment("localhost", 6123); // Flink JobManager RPC 端口
        env.setParallelism(1);
        env.enableCheckpointing(60000);
        env.getCheckpointConfig().setCheckpointStorage("file:///tmp/flink/checkpoints");
        env.getCheckpointConfig().setCheckpointingMode(CheckpointingMode.EXACTLY_ONCE);

        // 2. 创建表环境
        EnvironmentSettings settings = EnvironmentSettings.newInstance().inStreamingMode().build();
        StreamTableEnvironment tEnv = StreamTableEnvironment.create(env, settings);

//        # 3. 定义 Kafka Source Table
//        # 这里的Schema必须与user_actions_generator.py发送的JSON字典结构完全一致
//        # 字段名、类型都必须匹配
        TableResult kafka_source_result = tEnv.executeSql(
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
                        "    city_id INT," +
                        "    proc_time AS PROCTIME()" +
                        ") WITH (" +
                        "    'connector' = 'kafka'," +
                        "    'topic' = 'user_behavior'," +
//                        "    'properties.bootstrap.servers' = 'localhost:9092'," +
                        "    'properties.bootstrap.servers' = 'kafka:9093'," +
                        "    'properties.group.id' = 'flink_consumer_group'," +
                        "    'scan.startup.mode' = 'latest-offset'," +
                        "    'format' = 'json'" +
                        ")"
        );
        System.out.println("Flink Job submitted. Job ID: " + kafka_source_result.getJobClient()); // 获取 Job ID
        System.out.println("CREATE TABLE KafkaSource executed."); // 添加日志

//        # 4. 定义 MinIO (S3) Sink Table for Parquet with Time Partitioning
//        # 最终数据会写入MinIO，以Parquet格式，并按日期和小时分区
//        # 这里的Schema是你希望写入S3的最终结构
        TableResult minio_result = tEnv.executeSql(
                "CREATE TABLE MinIOSink (" +
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
                        "    'path' = 's3://flink-bucket/user_action/'," +
                        "    'format' = 'parquet'," +
                        "    'sink.rolling-policy.file-size' = '100MB'," +
                        "    'sink.rolling-policy.rollover-interval' = '1 min'," +
                        "    'sink.partition-commit.policy.kind' = 'success-file'," +
                        "    'sink.partition-commit.trigger' = 'process-time'" +
                        ")"
        );
        System.out.println("Flink Job submitted. Job minio ID: " + minio_result.getJobClient()); // 获取 Job ID
        System.out.println("CREATE TABLE MinIOSink executed."); // 添加日志

//        # 5. 核心 ETL 逻辑：从 KafkaSource 读取，富化，并插入到 MinIOSink
//        # 这里我们将原始时间戳转换为日期和小时，用于分区。
//        # 请注意字段名的对应关系，确保从KafkaSource中选择的字段与MinIOSink的Schema匹配
        TableResult result = tEnv.executeSql(
                "INSERT INTO MinIOSink " +
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
                        "FROM KafkaSource"
        );
        System.out.println("INSERT INTO MinIOSink SELECT executed."); // 添加日志
        System.out.println("Flink Job submitted. Job ID: " + result.getJobClient().get().getJobID()); // 获取 Job ID

//        env.execute("KafkaConsumerJob");

        System.out.printf("Pyflink job submitted！！！！！check it by kafka-ui:http://localhost:8080 and minio:http://localhost:9001/browser");
    }
}
