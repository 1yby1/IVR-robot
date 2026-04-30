package com.ivr.ai.rag;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 内存兜底 / 测试用 {@link KnowledgeService} 实现：基于关键词次数排序。
 *
 * <p>**不再注册为 Spring Bean**——容器中的实现固定为 {@link DatabaseKnowledgeService}。
 * 这个类保留下来仅用于：
 * <ul>
 *   <li>单元测试里直接 {@code new InMemoryKnowledgeService()} 验证 RAG 节点链路；</li>
 *   <li>未来如需"无数据库"调试模式时通过 {@code @Bean} 显式装配。</li>
 * </ul>
 */
public class InMemoryKnowledgeService implements KnowledgeService {

    private static final int DEFAULT_TOP_K = 3;

    private final List<KnowledgeChunk> chunks = List.of(
            new KnowledgeChunk("faq-1", "营业时间",
                    "我们的客服热线 7×24 小时不间断服务。人工坐席工作时间为每天 9:00 至 21:00。",
                    0.0),
            new KnowledgeChunk("faq-2", "查询账单",
                    "您可以在 App 或官网登录后，从「我的-账单」入口查看最近 12 个月的账单明细，也可拨打 95XXX 让客服代查。",
                    0.0),
            new KnowledgeChunk("faq-3", "投诉建议",
                    "对服务不满意可在通话中按 0 转人工，或通过官网「联系我们」提交工单，我们会在 24 小时内回复。",
                    0.0),
            new KnowledgeChunk("faq-4", "套餐变更",
                    "套餐变更需在每月 1-25 日发起，次月 1 日生效；如需立即生效请联系客服办理。",
                    0.0)
    );

    @Override
    public List<KnowledgeChunk> retrieve(Long kbId, String question, int topK) {
        int limit = topK > 0 ? topK : DEFAULT_TOP_K;
        if (!StringUtils.hasText(question)) {
            return List.of();
        }
        List<String> keywords = KnowledgeTextUtils.tokenize(question);
        List<KnowledgeChunk> scored = new ArrayList<>();
        for (KnowledgeChunk chunk : chunks) {
            double score = KnowledgeTextUtils.scoreContent(chunk.title() + " " + chunk.content(), keywords);
            if (score > 0) {
                scored.add(new KnowledgeChunk(chunk.docId(), chunk.title(), chunk.content(), score));
            }
        }
        scored.sort(Comparator.comparingDouble(KnowledgeChunk::score).reversed());
        return scored.size() > limit ? scored.subList(0, limit) : scored;
    }
}
