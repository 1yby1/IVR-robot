package com.ivr.ai.rag.vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class PgvectorStore implements VectorStore {

    private static final Logger log = LoggerFactory.getLogger(PgvectorStore.class);
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final PgvectorProperties properties;

    public PgvectorStore(PgvectorProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean available() {
        return properties.isEnabled()
                && StringUtils.hasText(properties.getJdbcUrl())
                && StringUtils.hasText(properties.getUsername())
                && safeTableName() != null;
    }

    @Override
    public void upsert(Long chunkId, Long kbId, Long docId, float[] embedding) {
        if (!available() || chunkId == null || kbId == null || docId == null || embedding == null || embedding.length == 0) {
            return;
        }
        String sql = """
                INSERT INTO %s (chunk_id, kb_id, doc_id, embedding, updated_at)
                VALUES (?, ?, ?, ?::vector, CURRENT_TIMESTAMP)
                ON CONFLICT (chunk_id) DO UPDATE SET
                  kb_id = EXCLUDED.kb_id,
                  doc_id = EXCLUDED.doc_id,
                  embedding = EXCLUDED.embedding,
                  updated_at = CURRENT_TIMESTAMP
                """.formatted(safeTableName());
        try (Connection conn = connection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, chunkId);
            ps.setLong(2, kbId);
            ps.setLong(3, docId);
            ps.setString(4, vectorLiteral(embedding));
            ps.executeUpdate();
        } catch (SQLException e) {
            log.warn("[Pgvector] upsert failed chunk={} err={}", chunkId, e.toString());
        }
    }

    @Override
    public void deleteByDocId(Long docId) {
        if (!available() || docId == null) {
            return;
        }
        String sql = "DELETE FROM %s WHERE doc_id = ?".formatted(safeTableName());
        try (Connection conn = connection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, docId);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.warn("[Pgvector] delete failed doc={} err={}", docId, e.toString());
        }
    }

    @Override
    public List<VectorHit> search(Long kbId, float[] queryVector, int topK) {
        if (!available() || queryVector == null || queryVector.length == 0 || topK <= 0) {
            return List.of();
        }
        StringBuilder sql = new StringBuilder("""
                SELECT chunk_id, doc_id, 1 - (embedding <=> ?::vector) AS score
                FROM %s
                WHERE embedding IS NOT NULL
                """.formatted(safeTableName()));
        if (kbId != null) {
            sql.append(" AND kb_id = ?");
        }
        if (properties.getMinScore() > 0) {
            sql.append(" AND 1 - (embedding <=> ?::vector) >= ?");
        }
        sql.append(" ORDER BY embedding <=> ?::vector LIMIT ?");

        List<VectorHit> hits = new ArrayList<>();
        String vector = vectorLiteral(queryVector);
        try (Connection conn = connection()) {
            tuneSearch(conn);
            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                int index = 1;
                ps.setString(index++, vector);
                if (kbId != null) {
                    ps.setLong(index++, kbId);
                }
                if (properties.getMinScore() > 0) {
                    ps.setString(index++, vector);
                    ps.setDouble(index++, properties.getMinScore());
                }
                ps.setString(index++, vector);
                ps.setInt(index, topK);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        hits.add(new VectorHit(
                                rs.getLong("chunk_id"),
                                rs.getLong("doc_id"),
                                rs.getDouble("score")));
                    }
                }
            }
        } catch (SQLException e) {
            log.warn("[Pgvector] search failed kb={} err={}", kbId, e.toString());
            return List.of();
        }
        return hits;
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(properties.getJdbcUrl(), properties.getUsername(), properties.getPassword());
    }

    private void tuneSearch(Connection conn) {
        if (properties.getEfSearch() <= 0) {
            return;
        }
        try (Statement statement = conn.createStatement()) {
            statement.execute("SET hnsw.ef_search = " + properties.getEfSearch());
        } catch (SQLException e) {
            log.debug("[Pgvector] skip hnsw.ef_search tuning: {}", e.toString());
        }
    }

    private String safeTableName() {
        String tableName = properties.getTableName();
        if (!StringUtils.hasText(tableName) || !SAFE_IDENTIFIER.matcher(tableName).matches()) {
            return null;
        }
        return tableName;
    }

    private String vectorLiteral(float[] vector) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(Float.toString(vector[i]));
        }
        return builder.append(']').toString();
    }
}
