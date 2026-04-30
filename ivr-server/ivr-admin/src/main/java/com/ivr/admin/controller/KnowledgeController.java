package com.ivr.admin.controller;

import com.ivr.admin.dto.KnowledgeBaseRequest;
import com.ivr.admin.dto.KnowledgeDocRequest;
import com.ivr.admin.service.KnowledgeAdminService;
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

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/knowledge")
public class KnowledgeController {

    private final KnowledgeAdminService knowledgeAdminService;

    public KnowledgeController(KnowledgeAdminService knowledgeAdminService) {
        this.knowledgeAdminService = knowledgeAdminService;
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
}
