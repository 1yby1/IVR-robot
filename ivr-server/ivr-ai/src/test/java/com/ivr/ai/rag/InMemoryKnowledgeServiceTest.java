package com.ivr.ai.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryKnowledgeServiceTest {

    private final InMemoryKnowledgeService service = new InMemoryKnowledgeService();

    @Test
    void retrieve_byChineseKeyword_hits() {
        List<KnowledgeService.KnowledgeChunk> result = service.retrieve(null, "营业时间是几点", 3);
        assertThat(result).isNotEmpty();
        assertThat(result.get(0).docId()).isEqualTo("faq-1");
    }

    @Test
    void retrieve_billQuery_hitsBillFaq() {
        List<KnowledgeService.KnowledgeChunk> result = service.retrieve(null, "怎么查账单", 3);
        assertThat(result).isNotEmpty();
        assertThat(result.get(0).docId()).isEqualTo("faq-2");
    }

    @Test
    void retrieve_unrelatedQuery_returnsEmpty() {
        List<KnowledgeService.KnowledgeChunk> result = service.retrieve(null, "asdf qwer", 3);
        assertThat(result).isEmpty();
    }

    @Test
    void retrieve_emptyQuestion_returnsEmpty() {
        assertThat(service.retrieve(null, "", 3)).isEmpty();
        assertThat(service.retrieve(null, null, 3)).isEmpty();
    }

    @Test
    void retrieve_topKBoundsResult() {
        List<KnowledgeService.KnowledgeChunk> result = service.retrieve(null, "套餐 投诉 账单", 1);
        assertThat(result).hasSize(1);
    }
}
