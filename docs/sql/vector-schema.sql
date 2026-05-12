CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS kb_chunk_vector (
  chunk_id   BIGINT PRIMARY KEY,
  kb_id      BIGINT NOT NULL,
  doc_id     BIGINT NOT NULL,
  embedding  vector(1024) NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_kb_chunk_vector_kb
ON kb_chunk_vector(kb_id);

CREATE INDEX IF NOT EXISTS idx_kb_chunk_vector_doc
ON kb_chunk_vector(doc_id);

CREATE INDEX IF NOT EXISTS idx_kb_chunk_vector_embedding_hnsw
ON kb_chunk_vector
USING hnsw (embedding vector_cosine_ops);
