package com.ivr.admin.dto;

import java.util.ArrayList;
import java.util.List;

public class KnowledgeRetrievalDebugResponse {

    private Long kbId;
    private String question;
    private Integer topK;
    private String answerStatus;
    private String answer;
    private String error;
    private String prompt;
    private List<Chunk> chunks = new ArrayList<>();

    public Long getKbId() { return kbId; }
    public void setKbId(Long kbId) { this.kbId = kbId; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public Integer getTopK() { return topK; }
    public void setTopK(Integer topK) { this.topK = topK; }
    public String getAnswerStatus() { return answerStatus; }
    public void setAnswerStatus(String answerStatus) { this.answerStatus = answerStatus; }
    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
    public List<Chunk> getChunks() { return chunks; }
    public void setChunks(List<Chunk> chunks) { this.chunks = chunks; }

    public static class Chunk {
        private String docId;
        private String title;
        private String content;
        private Double score;

        public String getDocId() { return docId; }
        public void setDocId(String docId) { this.docId = docId; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public Double getScore() { return score; }
        public void setScore(Double score) { this.score = score; }
    }
}
