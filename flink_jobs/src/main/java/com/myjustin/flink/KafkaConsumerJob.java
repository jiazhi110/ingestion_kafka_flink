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
        env.setParallelism(1);
        env.enableCheckpointing(60000);
        // 对于本地文件系统 Sink，推荐使用 file:// 协议头
        env.getCheckpointConfig().setCheckpointStorage("file:///tmp/flink/checkpoints");
        env.getCheckpointConfig().setCheckpointingMode(CheckpointingMode.EXACTLY_ONCE);

        // 2. 创建表环境
        EnvironmentSettings settings = EnvironmentSettings.newInstance().inStreamingMode().build();
        StreamTableEnvironment tEnv = StreamTableEnvironment.create(env, settings);

        // 3. 定义 Kafka Source 表 (DDL)
        // DDL 语句只是注册元数据，不会立即执行任务
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
                        "    'connector' = 'kafka'," +
                        "    'topic' = 'user_behavior'," +
                        "    'properties.bootstrap.servers' = 'kafka:9093'," +
                        "    'properties.group.id' = 'flink_consumer_group'," +
                        "    'scan.startup.mode' = 'latest-offset'," +
                        "    'format' = 'json'" +
                        ")"
        );
        System.out.println("CREATE TABLE KafkaSource executed.");

        // 4. 定义本地文件系统 Sink 表 (DDL)
        tEnv.executeSql(
                "CREATE TABLE LocalFileSink (" + // 为了清晰，改个名字
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
//                        "    'path'='file:///tmp/output/user_action/'," +
                        "    'path'='s3://flink-bucket/user_action/'," +
                        // "    s3://jiazhi110-flink-staging-bucket/user_action/" +
                        "    'sink.rolling-policy.file-size' = '100MB'," +
                        "    'sink.rolling-policy.rollover-interval' = '1 min'," +
                        "    'sink.partition-commit.policy.kind' = 'success-file'," +
                        "    'sink.partition-commit.trigger' = 'process-time'" +
                        ")"
        );
        System.out.println("CREATE TABLE LocalFileSink executed.");

        // 5. 定义核心 ETL 逻辑 (DML)
        // 这一步定义了数据流图，但还未真正启动
        TableResult result = tEnv.executeSql(
                "INSERT INTO LocalFileSink " +
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
        System.out.println("INSERT INTO statement has been submitted to the job graph.");

        // 🔥 6. 真正启动并执行 Flink 任务 🔥
        // 这是最重要的、被您注释掉的一行。它会阻塞 main 线程，让 Flink 任务持续运行。
        // 我们不再需要 env.execute()，因为 tEnv.executeSql("INSERT...") 已经提交了任务。
        // 我们需要调用 result.await() 来等待任务完成（对于流处理，这意味着永远等待）。
        System.out.println("Flink job graph defined. Now waiting for the job to run and finish...");
        result.await();

        // 下面的代码将不会被执行，除非任务被取消或失败
        System.out.println("This line will only be printed if the job is cancelled or fails.");
    }
}
