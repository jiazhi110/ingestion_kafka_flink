package com.myjustin.flink;

import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.TableResult;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.flink.types.Row;
import org.apache.flink.util.CloseableIterator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class KafkaConsumerJobTest {

    @Test
    public void testSqlTransformationLogic() throws Exception {
        // 1. 创建测试环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        EnvironmentSettings settings = EnvironmentSettings.newInstance().inStreamingMode().build();
        StreamTableEnvironment tEnv = StreamTableEnvironment.create(env, settings);

        // 2. 准备测试数据 (模拟 KafkaSource)
        // action_time: 1672531200000 对应 UTC 时间 2023-01-01 00:00:00
        // action_time: 1672567800000 对应 UTC 时间 2023-01-01 10:10:00
        String createSourceTableSql = "CREATE TABLE KafkaSource (" +
                "    `action_time` BIGINT," +
                "    `user_id` INT" +
                // 为简化，我们只保留测试逻辑需要的字段
                ") WITH (" +
                "    'connector' = 'values'," + // 使用 'values' 连接器来创建内存表
                "    'data-id' = '" +
                "       +I[1672531200000, 101]," +
                "       +I[1672567800000, 102]" +
                "   '" +
                ")";
        tEnv.executeSql(createSourceTableSql);


        // 3. 执行你的核心 SQL 查询逻辑
        // 我们只查询，不插入到 S3，而是将结果物化为一个 Table 对象
        Table resultTable = tEnv.sqlQuery(
                "SELECT " +
                        "    user_id, " +
                        "    action_time AS action_time_ms, " +
                        "    DATE_FORMAT(FROM_UNIXTIME(action_time / 1000), 'yyyy-MM-dd') AS dt, " +
                        "    DATE_FORMAT(FROM_UNIXTIME(action_time / 1000), 'HH') AS hr " +
                        "FROM KafkaSource"
        );

        // 4. 收集并验证结果
        // 将 Table 转换为数据流，并收集结果到 List<Row>
        CloseableIterator<Row> iterator = resultTable.execute().collect();
        // 将 Iterator 转换为更容易断言的 List
        Iterable<Row> iterable = () -> iterator;
        List<Row> results = StreamSupport.stream(iterable.spliterator(), false)
                .collect(Collectors.toList());


        // 5. 断言结果是否符合预期
        assertEquals(2, results.size(), "Should receive two records");

        // 验证第一条记录
        Row row1 = results.get(0);
        assertEquals(101, row1.getField("user_id"));
        assertEquals(1672531200000L, row1.getField("action_time_ms"));
        assertEquals("2023-01-01", row1.getField("dt").toString()); // .toString()
        assertEquals("00", row1.getField("hr").toString());

        // 验证第二条记录
        Row row2 = results.get(1);
        assertEquals(102, row2.getField("user_id"));
        assertEquals(1672567800000L, row2.getField("action_time_ms"));
        assertEquals("2023-01-01", row2.getField("dt").toString());
        assertEquals("10", row2.getField("hr").toString());

        iterator.close(); // 别忘了关闭
    }
}