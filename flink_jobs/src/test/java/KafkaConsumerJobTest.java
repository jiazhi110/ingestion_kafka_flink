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
 * Standard and stable unit test template for Flink SQL jobs.
 * The core idea is to use POJOs to define data and UDFs (User-Defined Functions)
 * to handle complex or inconsistent behaviors, ensuring robustness and clarity.
 */
public class KafkaConsumerJobTest {

    /**
     * Custom POJO class for encapsulating test data.
     * Compared to Row types, POJOs provide better readability and type safety.
     * Flink requires POJO classes to be public and have a public no-args constructor.
     */
    public static class Event {
        public long action_time;
        public int user_id;

        // Flink POJO requirement: public no-args constructor
        public Event() {}

        public Event(long action_time, int user_id) {
            this.action_time = action_time;
            this.user_id = user_id;
        }
    }

    /**
     * Create a User-Defined Function (UDF) to format UTC dates.
     * This function uses Java 8 standard time library, explicitly specifying UTC timezone,
     * ensuring deterministic behavior and avoiding dependency on Flink's potentially unstable timezone handling.
     */
    public static class UTCDateFormatter extends ScalarFunction {
        // Define thread-safe formatter
        private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);
        private static final DateTimeFormatter HOUR_FORMATTER = DateTimeFormatter.ofPattern("HH").withZone(ZoneOffset.UTC);

        // Flink will call this 'eval' method
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
        // 1. Create test environment
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        EnvironmentSettings settings = EnvironmentSettings.newInstance().inStreamingMode().build();
        StreamTableEnvironment tEnv = StreamTableEnvironment.create(env, settings);

        // Register our custom function
        tEnv.createTemporarySystemFunction("UTC_DATE_FORMAT", UTCDateFormatter.class);

        System.out.println("System TZ: " + java.time.ZoneId.systemDefault());
        System.out.println("Flink Table TZ: " + tEnv.getConfig().getLocalTimeZone());

        // 2. Prepare test data (Use POJO for type safety and readability)
        List<Event> testData = Arrays.asList(
                new Event(1672531200000L, 101),
                new Event(1672567800000L, 102)
        );

        // Create DataStream from POJO list
        DataStream<Event> testDataStream = env.fromCollection(testData);

        // Create view directly from DataStream<POJO>, Flink infers column names from POJO fields
        tEnv.createTemporaryView("KafkaSource", testDataStream);

        // 3. Execute core SQL query logic
        // Use our own 100% reliable function in SQL
        Table resultTable = tEnv.sqlQuery(
                "SELECT " +
                        "    user_id, " +
                        "    action_time AS action_time_ms, " +
                        "    UTC_DATE_FORMAT(action_time, 'yyyy-MM-dd') AS dt, " +
                        "    UTC_DATE_FORMAT(action_time, 'HH') AS hr " +
                        "FROM KafkaSource"
        );

        // 4. Collect and verify results
        CloseableIterator<Row> iterator = resultTable.execute().collect();
        Iterable<Row> iterable = () -> iterator;
        List<Row> results = StreamSupport.stream(iterable.spliterator(), false)
                .collect(Collectors.toList());

        // 5. Assert results match expectations
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
