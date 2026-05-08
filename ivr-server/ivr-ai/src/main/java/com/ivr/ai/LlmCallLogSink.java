package com.ivr.ai;

public interface LlmCallLogSink {

    void record(LlmCallLogRecord record);
}
