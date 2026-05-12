package com.ivr.ai.rag.vector;

import java.util.List;

public interface VectorStore {

    boolean available();

    void upsert(Long chunkId, Long kbId, Long docId, float[] embedding);

    void deleteByDocId(Long docId);

    List<VectorHit> search(Long kbId, float[] queryVector, int topK);
}
