package com.ivr.ai.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 关键词检索的分词与打分工具。
 *
 * <p>规则：CJK 字符按单字切分；非 CJK 按空格 / 标点切分并小写；过滤单字符的拉丁词
 * （噪声）。打分用关键词在文本中的出现次数加权累加。
 *
 * <p>包私有 —— 仅供 {@link InMemoryKnowledgeService} 与 {@link DatabaseKnowledgeService} 复用。
 */
final class KnowledgeTextUtils {

    private static final String PUNCTUATION = ",.;:!?，。；：！？、\"'()[]{}（）【】《》";

    private KnowledgeTextUtils() {
    }

    static List<String> tokenize(String text) {
        List<String> keywords = new ArrayList<>();
        String normalized = Objects.toString(text, "");
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (Character.isWhitespace(c) || isPunctuation(c)) {
                flush(current, keywords);
            } else if (isCjk(c)) {
                flush(current, keywords);
                keywords.add(String.valueOf(c));
            } else {
                current.append(Character.toLowerCase(c));
            }
        }
        flush(current, keywords);
        // 过滤单字符的非 CJK 噪声（如 "a", "i"）
        List<String> filtered = new ArrayList<>(keywords.size());
        for (String token : keywords) {
            if (token.length() > 1 || (!token.isEmpty() && isCjk(token.charAt(0)))) {
                filtered.add(token);
            }
        }
        return filtered;
    }

    static double scoreContent(String content, List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return 0;
        }
        String text = Objects.toString(content, "").toLowerCase();
        if (text.isEmpty()) {
            return 0;
        }
        double score = 0;
        for (String kw : keywords) {
            if (kw == null || kw.isEmpty()) {
                continue;
            }
            int idx = 0;
            while ((idx = text.indexOf(kw, idx)) >= 0) {
                score += kw.length();
                idx += kw.length();
            }
        }
        return score;
    }

    private static void flush(StringBuilder current, List<String> keywords) {
        if (current.length() > 0) {
            keywords.add(current.toString());
            current.setLength(0);
        }
    }

    private static boolean isCjk(char c) {
        return c >= 0x4E00 && c <= 0x9FFF;
    }

    private static boolean isPunctuation(char c) {
        return PUNCTUATION.indexOf(c) >= 0;
    }
}
