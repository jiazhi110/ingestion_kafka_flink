import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.flink.table.functions.ScalarFunction;
import org.apache.flink.types.Row;
import org.apache.flink.util.CloseableIterator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 这是 Flink SQL 作业最标准、最稳定的单元测试模板。
 * 核心思想是使用 POJO 定义数据，并通过 UDF (User-Defined Function)
 * 来处理复杂的或环境不一致的行为，确保测试的健壮与清晰。
 */
public class KafkaConsumerJobTest {

    /**
     * 自定义的 POJO 类，用于封装测试数据。
     * 相比 Row 类型，POJO 提供了更好的可读性和类型安全。
     * Flink 要求 POJO 类必须是 public，且拥有一个 public 的无参构造函数。
     */
    public static class Event {
        public long action_time;
        public int user_id;

        // Flink POJO 要求：public 无参构造函数
        public Event() {}

        public Event(long action_time, int user_id) {
            this.action_time = action_time;
            this.user_id = user_id;
        }
    }

    /**
     * 创建一个用户自定义函数 (UDF) 来格式化 UTC 日期。
     * 这个函数使用 Java 8 的标准时间库，显式指定 UTC 时区，行为完全确定，
     * 不再依赖 Flink 底层可能不稳定的时区处理。
     */
    public static class UTCDateFormatter extends ScalarFunction {
        // 定义线程安全的格式化器
        private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);
        private static final DateTimeFormatter HOUR_FORMATTER = DateTimeFormatter.ofPattern("HH").withZone(ZoneOffset.UTC);

        // Flink 会调用这个 'eval' 方法
        public String eval(Long timestamp_ms, String format) {
            if (timestamp_ms == null || format == null) {
                return null;
            }
            Instant instant = Instant.ofEpochMilli(timestamp_ms);
            if ("yyyy-MM-dd".equals(format)) {
                return DATE_FORMATTER.format(instant);
            }
            if ("HH".equals(format)) {
                return HOUR_FORMATTER.format(instant);
            }
            return null;
        }
    }

    @Test
    public void testSqlTransformationLogic() throws Exception {
        // 1. 创建测试环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        EnvironmentSettings settings = EnvironmentSettings.newInstance().inStreamingMode().build();
        StreamTableEnvironment tEnv = StreamTableEnvironment.create(env, settings);

        // 注册我们自定义的函数
        tEnv.createTemporarySystemFunction("UTC_DATE_FORMAT", UTCDateFormatter.class);

        System.out.println("System TZ: " + java.time.ZoneId.systemDefault());
        System.out.println("Flink Table TZ: " + tEnv.getConfig().getLocalTimeZone());

        // 2. 准备测试数据 (模拟 KafkaSource)   error:MiniCluster is not yet running or has already been shut down.
        // action_time: 1672531200000 对应 UTC 时间 2023-01-01 00:00:00
        // action_time: 1672567800000 对应 UTC 时间 2023-01-01 10:10:00
//        String createSourceTableSql = "CREATE TABLE KafkaSource (" +
////                "    `action_time` BIGINT," +
////                "    `user_id` INT" +
////                // 为简化，我们只保留测试逻辑需要的字段
////                ") WITH (" +
////                "    'connector' = 'values'," + // 使用 'values' 连接器来创建内存表
////                "    'bounded' = 'true'" + // 数据是有界的（bounded），插入完就结束，Flink 作业会自然结束
////                ")";
////        tEnv.executeSql(createSourceTableSql);
////
////        // 插入
////        tEnv.executeSql(
////                "INSERT INTO KafkaSource VALUES " +
////                        "(1672531200000, 101), " +
////                        "(1672567800000, 102)"
////        ).await();

        // 2. 准备测试数据 (使用 POJO，更类型安全和可读)
        List<Event> testData = Arrays.asList(
                new Event(1672531200000L, 101),
                new Event(1672567800000L, 102)
        );

        // 从 POJO 列表创建 DataStream
        DataStream<Event> testDataStream = env.fromCollection(testData);

        // 直接从 DataStream<POJO> 创建视图，Flink 会自动根据 POJO 的字段名推断列名
        tEnv.createTemporaryView("KafkaSource", testDataStream);

        // 我们只查询，不插入到 S3，而是将结果物化为一个 Table 对象,源代码：KafkaConsumerJob
//        Table resultTable = tEnv.sqlQuery(
//                "SELECT " +
//                        "    user_id, " +
//                        "    action_time AS action_time_ms, " +
//                        "    DATE_FORMAT(FROM_UNIXTIME(action_time / 1000), 'yyyy-MM-dd') AS dt, " +
//                        "    DATE_FORMAT(FROM_UNIXTIME(action_time / 1000), 'HH') AS hr " +
//                        "FROM KafkaSource"
//        );

//        这个是用最常用的方式 tEnv.getConfig().setLocalTimeZone(ZoneId.of("UTC")); + TO_TIMESTAMP_LTZ 的方式，将两个时间同步，但是本地还是报错 ：MiniCluster is not yet running or has already been shut down.
//        Table resultTable = tEnv.sqlQuery(
//                "SELECT " +
//                        "    user_id, " +
//                        "    action_time AS action_time_ms, " +
//                        "    DATE_FORMAT(TO_TIMESTAMP_LTZ(action_time, 3), 'yyyy-MM-dd') AS dt, " +
//                        "    DATE_FORMAT(TO_TIMESTAMP_LTZ(action_time, 3), 'HH') AS hr " +
//                        "FROM KafkaSource"
//        );

        // 3. 执行核心 SQL 查询逻辑
        // 在 SQL 中使用我们自己注册的、行为100%可靠的函数
        Table resultTable = tEnv.sqlQuery(
                "SELECT " +
                        "    user_id, " +
                        "    action_time AS action_time_ms, " +
                        "    UTC_DATE_FORMAT(action_time, 'yyyy-MM-dd') AS dt, " +
                        "    UTC_DATE_FORMAT(action_time, 'HH') AS hr " +
                        "FROM KafkaSource"
        );

        // 4. 收集并验证结果
        CloseableIterator<Row> iterator = resultTable.execute().collect();
        Iterable<Row> iterable = () -> iterator;
        List<Row> results = StreamSupport.stream(iterable.spliterator(), false)
                .collect(Collectors.toList());

        // 5. 断言结果是否符合预期
        assertEquals(2, results.size(), "Should receive two records");
        results.sort(Comparator.comparingInt(row -> (Integer) row.getField("user_id")));

        Row row1 = results.get(0);
        assertEquals(101, row1.getField("user_id"));
        assertEquals(1672531200000L, row1.getField("action_time_ms"));
        assertEquals("2023-01-01", row1.getField("dt").toString());
        assertEquals("00", row1.getField("hr").toString());

        Row row2 = results.get(1);
        assertEquals(102, row2.getField("user_id"));
        assertEquals(1672567800000L, row2.getField("action_time_ms"));
        assertEquals("2023-01-01", row2.getField("dt").toString());
        assertEquals("10", row2.getField("hr").toString());

        iterator.close();
    }
}

