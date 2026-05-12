package com.ivr.ai.rag.vector;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ivr.rag.pgvector")
public class PgvectorProperties {

    private boolean enabled;
    private String jdbcUrl = "jdbc:postgresql://localhost:5432/ivr_vector";
    private String username = "ivr";
    private String password = "ivr123";
    private String tableName = "kb_chunk_vector";
    private double minScore = 0.0;
    private int efSearch = 100;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public void setJdbcUrl(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public double getMinScore() {
        return minScore;
    }

    public void setMinScore(double minScore) {
        this.minScore = minScore;
    }

    public int getEfSearch() {
        return efSearch;
    }

    public void setEfSearch(int efSearch) {
        this.efSearch = efSearch;
    }
}
