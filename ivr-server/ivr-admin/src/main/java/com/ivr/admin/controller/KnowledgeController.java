package com.ivr.admin.controller;

import com.ivr.admin.dto.KnowledgeBaseRequest;
import com.ivr.admin.dto.KnowledgeChunkPreviewRequest;
import com.ivr.admin.dto.KnowledgeChunkPreviewResponse;
import com.ivr.admin.dto.KnowledgeDocParseResponse;
import com.ivr.admin.dto.KnowledgeDocRequest;
import com.ivr.admin.dto.KnowledgeRetrievalDebugRequest;
import com.ivr.admin.dto.KnowledgeRetrievalDebugResponse;
import com.ivr.admin.dto.RagEvalCaseRequest;
import com.ivr.admin.dto.RagEvalRunRequest;
import com.ivr.admin.service.KnowledgeAdminService;
import com.ivr.admin.service.RagEvalService;
import com.ivr.common.result.R;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/knowledge")
public class KnowledgeController {

    private final KnowledgeAdminService knowledgeAdminService;
    private final RagEvalService ragEvalService;

    public KnowledgeController(KnowledgeAdminService knowledgeAdminService,
                               RagEvalService ragEvalService) {
        this.knowledgeAdminService = knowledgeAdminService;
        this.ragEvalService = ragEvalService;
    }

    @GetMapping("/bases/page")
    public R<Map<String, Object>> pageBases(@RequestParam(defaultValue = "1") int current,
                                            @RequestParam(defaultValue = "10") int size,
                                            @RequestParam(required = false) String keyword) {
        return R.ok(knowledgeAdminService.pageBases(current, size, keyword));
    }

    @GetMapping("/bases/options")
    public R<List<Map<String, Object>>> baseOptions() {
        return R.ok(knowledgeAdminService.baseOptions());
    }

    @PostMapping("/bases")
    public R<Long> createBase(@Valid @RequestBody KnowledgeBaseRequest request) {
        return R.ok(knowledgeAdminService.createBase(request));
    }

    @PutMapping("/bases/{id}")
    public R<Void> updateBase(@PathVariable Long id, @Valid @RequestBody KnowledgeBaseRequest request) {
        knowledgeAdminService.updateBase(id, request);
        return R.ok();
    }

    @DeleteMapping("/bases/{id}")
    public R<Void> deleteBase(@PathVariable Long id) {
        knowledgeAdminService.deleteBase(id);
        return R.ok();
    }

    @GetMapping("/docs/page")
    public R<Map<String, Object>> pageDocs(@RequestParam(defaultValue = "1") int current,
                                           @RequestParam(defaultValue = "10") int size,
                                           @RequestParam(required = false) Long kbId,
                                           @RequestParam(required = false) String keyword) {
        return R.ok(knowledgeAdminService.pageDocs(current, size, kbId, keyword));
    }

    @GetMapping("/docs/{id}")
    public R<Map<String, Object>> docDetail(@PathVariable Long id) {
        return R.ok(knowledgeAdminService.docDetail(id));
    }

    @PostMapping("/docs")
    public R<Long> createDoc(@Valid @RequestBody KnowledgeDocRequest request) {
        return R.ok(knowledgeAdminService.createDoc(request));
    }

    @PostMapping("/docs/parse-file")
    public R<KnowledgeDocParseResponse> parseDocFile(@RequestParam("file") MultipartFile file) {
        return R.ok(knowledgeAdminService.parseDocFile(file));
    }

    @PostMapping("/docs/chunks/preview")
    public R<KnowledgeChunkPreviewResponse> previewChunks(@Valid @RequestBody KnowledgeChunkPreviewRequest request) {
        return R.ok(knowledgeAdminService.previewChunks(request.getContent()));
    }

    @PostMapping("/debug/retrieval")
    public R<KnowledgeRetrievalDebugResponse> debugRetrieval(@Valid @RequestBody KnowledgeRetrievalDebugRequest request) {
        return R.ok(knowledgeAdminService.debugRetrieval(request));
    }

    @PutMapping("/docs/{id}")
    public R<Void> updateDoc(@PathVariable Long id, @Valid @RequestBody KnowledgeDocRequest request) {
        knowledgeAdminService.updateDoc(id, request);
        return R.ok();
    }

    @PostMapping("/docs/{id}/reindex")
    public R<Void> reindexDoc(@PathVariable Long id) {
        knowledgeAdminService.reindexDoc(id);
        return R.ok();
    }

    @DeleteMapping("/docs/{id}")
    public R<Void> deleteDoc(@PathVariable Long id) {
        knowledgeAdminService.deleteDoc(id);
        return R.ok();
    }

    @GetMapping("/eval/cases/page")
    public R<Map<String, Object>> pageEvalCases(@RequestParam(defaultValue = "1") int current,
                                                @RequestParam(defaultValue = "10") int size,
                                                @RequestParam(required = false) Long kbId,
                                                @RequestParam(required = false) String keyword) {
        return R.ok(ragEvalService.pageCases(current, size, kbId, keyword));
    }

    @PostMapping("/eval/cases")
    public R<Long> createEvalCase(@Valid @RequestBody RagEvalCaseRequest request) {
        return R.ok(ragEvalService.createCase(request));
    }

    @PutMapping("/eval/cases/{id}")
    public R<Void> updateEvalCase(@PathVariable Long id, @Valid @RequestBody RagEvalCaseRequest request) {
        ragEvalService.updateCase(id, request);
        return R.ok();
    }

    @DeleteMapping("/eval/cases/{id}")
    public R<Void> deleteEvalCase(@PathVariable Long id) {
        ragEvalService.deleteCase(id);
        return R.ok();
    }

    @GetMapping("/eval/runs/page")
    public R<Map<String, Object>> pageEvalRuns(@RequestParam(defaultValue = "1") int current,
                                               @RequestParam(defaultValue = "10") int size,
                                               @RequestParam(required = false) Long kbId) {
        return R.ok(ragEvalService.pageRuns(current, size, kbId));
    }

    @PostMapping("/eval/runs")
    public R<Long> runEval(@Valid @RequestBody RagEvalRunRequest request) {
        return R.ok(ragEvalService.run(request));
    }

    @GetMapping("/eval/runs/{id}/results")
    public R<List<Map<String, Object>>> evalResults(@PathVariable Long id) {
        return R.ok(ragEvalService.results(id));
    }
}
