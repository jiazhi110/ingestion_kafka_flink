package com.myjustin.flink.model;

import org.apache.flink.table.annotation.DataTypeHint;
import java.time.LocalDateTime;

public class DlqRecord {
    public String raw_message;
    public String error_message;
    
    @DataTypeHint("TIMESTAMP(3)")
    public LocalDateTime processing_time;

    public DlqRecord() {}

    public DlqRecord(String raw_message, String error_message, LocalDateTime processing_time) {
        this.raw_message = raw_message;
        this.error_message = error_message;
        this.processing_time = processing_time;
    }
}