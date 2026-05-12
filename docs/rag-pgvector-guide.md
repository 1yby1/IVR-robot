# RAG pgvector 接入指南

## 1. 启动 pgvector

开发环境只启动向量库：

```bash
cd docker
docker compose up -d postgres-vector
```

完整环境：

```bash
cd docker
docker compose --profile full up -d
```

`postgres-vector` 会执行 `docs/sql/vector-schema.sql`，创建：

- `vector` extension
- `kb_chunk_vector` 向量表
- `hnsw` cosine 索引

## 2. 确认 Embedding 维度

`vector-schema.sql` 默认使用：

```sql
embedding vector(1024)
```

这个维度必须和当前 `EMBED_MODEL` 输出维度一致。如果更换模型，先确认 `embeddingModel.embed("测试").length`，再调整 `vector-schema.sql` 的 `vector(1024)`。

## 3. 开启后端 pgvector

本地运行后端前设置：

```powershell
$env:PGVECTOR_ENABLED="true"
$env:PGVECTOR_JDBC_URL="jdbc:postgresql://localhost:5432/ivr_vector"
$env:PGVECTOR_USER="ivr"
$env:PGVECTOR_PASSWORD="ivr123"
```

可选配置：

```powershell
$env:PGVECTOR_MIN_SCORE="0.2"
$env:PGVECTOR_EF_SEARCH="100"
```

未开启 `PGVECTOR_ENABLED` 时，系统会继续使用原来的 MySQL JSON 向量 + 关键词兜底检索。

## 4. 重建知识索引

pgvector 表只会在文档保存、更新或重建索引时写入。已有文档需要在前端“知识文档”页面点击“重建索引”，或者重新保存文档。

写入链路：

```text
文档内容 -> 切片 -> MySQL kb_chunk -> EmbeddingModel -> PostgreSQL kb_chunk_vector
```

## 5. 验证检索

进入“知识文档”或“RAG 评估”：

1. 确认文档状态为“已索引”
2. 运行 RAG 检索调试或评估
3. 命中结果仍会返回文档标题、切片内容和 score

检索链路：

```text
问题 -> query embedding -> pgvector TopK chunk_id -> MySQL 查询 chunk 内容和文档标题
```

如果 pgvector 查询失败或没有结果，系统会自动退回原有关键词/MySQL 检索逻辑。
