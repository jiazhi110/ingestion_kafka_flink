package com.myjustin.flink.function;

import com.myjustin.flink.model.DlqRecord;
import com.myjustin.flink.model.UserAction;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class UserActionMapperTest {

    private UserActionMapper mapper;
    private ProcessFunction.Context context;
    private Collector<UserAction> collector;

    @BeforeEach
    public void setup() throws Exception {
        mapper = new UserActionMapper();
        // Initialize the mapper (create ObjectMapper)
        mapper.open(new org.apache.flink.configuration.Configuration());
        
        context = Mockito.mock(ProcessFunction.Context.class);
        collector = Mockito.mock(Collector.class);
    }

    @Test
    public void testValidJson() throws Exception {
        String json = "{\"user_id\": 123, \"session_id\": \"sess_001\", \"action_time\": 1678888888000}";

        mapper.processElement(json, context, collector);

        // Verify that collector.collect was called once
        ArgumentCaptor<UserAction> captor = ArgumentCaptor.forClass(UserAction.class);
        verify(collector, times(1)).collect(captor.capture());

        UserAction result = captor.getValue();
        assertEquals(123, result.user_id);
        assertEquals("sess_001", result.session_id);
        assertEquals(1678888888000L, result.action_time);
        
        // Verify no side output was emitted
        verify(context, never()).output(any(OutputTag.class), any());
    }

    @Test
    public void testMissingUserId() throws Exception {
        // Missing user_id
        String json = "{\"session_id\": \"sess_001\"}";

        mapper.processElement(json, context, collector);

        // Verify main output is empty
        verify(collector, never()).collect(any());

        // Verify DLQ output
        ArgumentCaptor<DlqRecord> captor = ArgumentCaptor.forClass(DlqRecord.class);
        verify(context, times(1)).output(eq(UserActionMapper.DLQ_TAG), captor.capture());

        DlqRecord dlq = captor.getValue();
        assertEquals(json, dlq.raw_message);
        assertTrue(dlq.error_message.contains("Missing or null user_id"));
    }

    @Test
    public void testInvalidJsonFormat() throws Exception {
        // Malformed JSON
        String json = "{INVALID_JSON}";

        mapper.processElement(json, context, collector);

        // Verify main output is empty
        verify(collector, never()).collect(any());

        // Verify DLQ output
        ArgumentCaptor<DlqRecord> captor = ArgumentCaptor.forClass(DlqRecord.class);
        verify(context, times(1)).output(eq(UserActionMapper.DLQ_TAG), captor.capture());

        DlqRecord dlq = captor.getValue();
        assertEquals(json, dlq.raw_message);
        // Error message comes from Jackson parser
        assertNotNull(dlq.error_message);
    }
}
