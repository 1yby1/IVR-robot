package com.ivr.ai.rag.entity;

/**
 * 知识文档索引状态。值与数据库列 {@code kb_doc.status} 一一对应。
 */
public enum KbDocStatus {

    PENDING(0),
    INDEXING(1),
    INDEXED(2),
    FAILED(3);

    private final int code;

    KbDocStatus(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
