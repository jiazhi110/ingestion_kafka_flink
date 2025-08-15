package com.myjustin.flink;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.TableResult;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class KafkaConsumerJob {
    public static void main(String[] args) throws Exception {
        // Load configuration from properties file
        Properties props = new Properties();
        try (InputStream input = KafkaConsumerJob.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (input == null) {
                throw new IOException("Unable to find application.properties");
            }
            props.load(input);
        } catch (IOException e) {
            System.err.println("Failed to load application.properties: " + e.getMessage());
            throw e;
        }
        
        // Validate required properties
        String[] requiredProps = {
            "kafka.bootstrap.servers", "kafka.topic", "kafka.group.id",
            "kafka.security.protocol", "kafka.sasl.mechanism",
            "kafka.sasl.jaas.config", "kafka.sasl.client.callback.handler.class",
            "s3.output.path", "s3.checkpoint.path"
        };
        
        for (String prop : requiredProps) {
            if (props.getProperty(prop) == null) {
                throw new IllegalArgumentException("Missing required property: " + prop);
            }
        }

        // 1. 创建流执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(Integer.parseInt(props.getProperty("flink.parallelism", "1")));
        env.enableCheckpointing(Long.parseLong(props.getProperty("flink.checkpoint.interval", "60000")));
        // 对于本地文件系统 Sink，推荐使用 file:// 协议头
//        env.getCheckpointConfig().setCheckpointStorage("file:///tmp/flink/checkpoints");
        // produce set
        env.getCheckpointConfig().setCheckpointStorage(props.getProperty("s3.checkpoint.path"));
        env.getCheckpointConfig().setCheckpointingMode(CheckpointingMode.valueOf(props.getProperty("flink.checkpoint.mode", "EXACTLY_ONCE")));

        // 2. 创建表环境
        EnvironmentSettings settings = EnvironmentSettings.newInstance().inStreamingMode().build();
        StreamTableEnvironment tEnv = StreamTableEnvironment.create(env, settings);

//        // ECS 会将我们设置的 Secrets 注入为环境变量
//        String mskUsername = System.getenv("msk-username");
//        String mskPassword = System.getenv("msk-password");
//
//        // produce set
//        // 1：必须提供 MSK 提供的所有 Bootstrap Servers 地址，以实现高可用 🔥
//        String bootstrapServers = "b-2-public.flinkstagingkafkaclus.oj6v2z.c23.kafka.us-east-1.amazonaws.com:9196,b-1-public.flinkstagingkafkaclus.oj6v2z.c23.kafka.us-east-1.amazonaws.com:9196";
//
//        // 构建 JAAS 配置字符串
//        String jaasConfig = String.format(
//                "org.apache.kafka.common.security.scram.ScramLoginModule required username=\"%s\" password=\"%s\";",
//                mskUsername,
//                mskPassword
//        );

//        // 获取 Flink 的底层配置对象
//        Configuration configuration = tEnv.getConfig().getConfiguration();
//
//        // 将 Kafka 的安全认证配置，以编程方式设置进去
//        configuration.setString("properties.security.protocol", "SASL_SSL");
//        configuration.setString("properties.sasl.mechanism", "SCRAM-SHA-512");
//        configuration.setString("properties.sasl.jaas.config", jaasConfig);

        // 🔥 关键修改：不再需要从 Secrets Manager 读取用户名和密码

        // MSK 的 Bootstrap Servers 地址 (这次是 IAM 端口 9098)
//        String bootstrapServers = "b-1-public.flinkstagingkafkaclus.oj6v2z.c23.kafka.us-east-1.amazonaws.com:9198,b-2-public.flinkstagingkafkaclus.oj6v2z.c23.kafka.us-east-1.amazonaws.com:9198";
        // String bootstrapServers = "b-1.flinkstagingkafkaclus.oj6v2z.c23.kafka.us-east-1.amazonaws.com:9098,b-2.flinkstagingkafkaclus.oj6v2z.c23.kafka.us-east-1.amazonaws.com:9098";

        // // 构建 JAAS 配置字符串，使用 IAMLoginModule
        // String jaasConfig = "software.amazon.msk.auth.iam.IAMLoginModule required;";

        // // 获取 Flink 的底层配置对象
        // Configuration configuration = tEnv.getConfig().getConfiguration();

        // // 设置 Kafka 的安全认证配置
        // configuration.setString("properties.security.protocol", "SASL_SSL");
        // configuration.setString("properties.sasl.mechanism", "AWS_MSK_IAM");
        // configuration.setString("properties.sasl.jaas.config", jaasConfig);
        // configuration.setString("properties.sasl.client.callback.handler.class", "software.amazon.msk.auth.iam.IAMClientCallbackHandler");


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
                                "'connector' = 'kafka'," +
                                "'topic' = '" + props.getProperty("kafka.topic") + "'," +
                                "'properties.bootstrap.servers' = '" + props.getProperty("kafka.bootstrap.servers") + "'," +
                                "'properties.security.protocol' = '" + props.getProperty("kafka.security.protocol") + "'," +
                                "'properties.sasl.mechanism' = '" + props.getProperty("kafka.sasl.mechanism") + "'," +
                                "'properties.sasl.jaas.config' = '" + props.getProperty("kafka.sasl.jaas.config") + "'," +
                                "'properties.sasl.client.callback.handler.class' = '" + props.getProperty("kafka.sasl.client.callback.handler.class") + "'," +
                                "'properties.group.id' = '" + props.getProperty("kafka.group.id") + "'," +
                                "'scan.startup.mode' = 'latest-offset'," +
                                "'format' = 'json'" +
                        ")"
        );
        System.out.println("CREATE TABLE KafkaSource executed.");

        // 4. 定义本地文件系统 Sink 表 (DDL)
        tEnv.executeSql(
                "CREATE TABLE S3Sink (" + // 为了清晰，改个名字
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
                        // "    'path'='s3://flink-bucket/user_action/'," +
                        "    'path'='" + props.getProperty("s3.output.path") + "'," +
                        "    'sink.rolling-policy.file-size' = '100MB'," +
                        "    'sink.rolling-policy.rollover-interval' = '1 min'," +
                        "    'sink.partition-commit.policy.kind' = 'success-file'," +
                        "    'sink.partition-commit.trigger' = 'process-time'" +
                        ")"
        );
        System.out.println("CREATE TABLE S3Sink executed.");

        // 5. 定义核心 ETL 逻辑 (DML)
        // 这一步定义了数据流图，但还未真正启动
        TableResult result = tEnv.executeSql(
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
