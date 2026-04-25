package com.ivr.engine.session;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
@ConditionalOnMissingBean(SessionStore.class)
public class InMemorySessionStore implements SessionStore {

    private final ConcurrentMap<String, FlowSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void save(FlowSession session) {
        if (session == null || session.getSessionId() == null) {
            return;
        }
        sessions.put(session.getSessionId(), session);
    }

    @Override
    public FlowSession find(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        return sessions.get(sessionId);
    }

    @Override
    public void remove(String sessionId) {
        if (sessionId == null) {
            return;
        }
        sessions.remove(sessionId);
    }

    @Override
    public int size() {
        return sessions.size();
    }
}
