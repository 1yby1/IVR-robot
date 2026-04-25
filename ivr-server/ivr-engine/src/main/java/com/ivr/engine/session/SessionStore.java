package com.ivr.engine.session;

/**
 * 会话存储抽象。默认 {@link InMemorySessionStore}，生产可替换为 Redis 实现以支持多节点。
 */
public interface SessionStore {

    void save(FlowSession session);

    FlowSession find(String sessionId);

    void remove(String sessionId);

    int size();
}
