package com.ivr.admin.controller;

import com.ivr.admin.dto.FlowAiGenerateRequest;
import com.ivr.admin.dto.FlowAiGenerateResponse;
import com.ivr.admin.dto.FlowDebugInputRequest;
import com.ivr.admin.dto.FlowDebugResponse;
import com.ivr.admin.dto.FlowDebugStartRequest;
import com.ivr.admin.dto.FlowHealthResponse;
import com.ivr.admin.dto.FlowOptionItem;
import com.ivr.admin.service.FlowAiDraftService;
import com.ivr.admin.service.FlowDebugService;
import com.ivr.admin.service.FlowHealthService;
import com.ivr.admin.service.FlowStore;
import com.ivr.common.result.R;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/flow")
public class FlowController {

    private final FlowStore flowStore;
    private final FlowDebugService flowDebugService;
    private final FlowAiDraftService flowAiDraftService;
    private final FlowHealthService flowHealthService;

    public FlowController(FlowStore flowStore,
                          FlowDebugService flowDebugService,
                          FlowAiDraftService flowAiDraftService,
                          FlowHealthService flowHealthService) {
        this.flowStore = flowStore;
        this.flowDebugService = flowDebugService;
        this.flowAiDraftService = flowAiDraftService;
        this.flowHealthService = flowHealthService;
    }

    @GetMapping("/page")
    public R<Map<String, Object>> page(@RequestParam(defaultValue = "1") int current,
                                       @RequestParam(defaultValue = "10") int size,
                                       @RequestParam(required = false) String keyword) {
        return R.ok(flowStore.page(current, size, keyword));
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        return R.ok(flowStore.detail(id));
    }

    @GetMapping("/{id}/versions")
    public R<List<Map<String, Object>>> versions(@PathVariable Long id) {
        return R.ok(flowStore.versions(id));
    }

    @GetMapping("/{id}/health")
    public R<FlowHealthResponse> health(@PathVariable Long id) {
        return R.ok(flowHealthService.check(id));
    }

    @GetMapping("/published-options")
    public R<List<FlowOptionItem>> publishedOptions() {
        return R.ok(flowStore.publishedOptions());
    }

    @PostMapping
    public R<Long> create(@RequestBody Map<String, Object> body,
                          @AuthenticationPrincipal Long currentUserId) {
        return R.ok(flowStore.create(body, currentUserId));
    }

    @PostMapping("/ai/generate")
    public R<FlowAiGenerateResponse> generateByAi(@Valid @RequestBody FlowAiGenerateRequest request) {
        return R.ok(flowAiDraftService.generate(request));
    }

    @PutMapping("/{id}")
    public R<Void> save(@PathVariable Long id,
                        @RequestBody Map<String, Object> body,
                        @AuthenticationPrincipal Long currentUserId) {
        flowStore.save(id, body, currentUserId);
        return R.ok();
    }

    @PostMapping("/{id}/publish")
    public R<Void> publish(@PathVariable Long id,
                           @AuthenticationPrincipal Long currentUserId) {
        flowStore.publish(id, currentUserId);
        return R.ok();
    }

    @PostMapping("/{id}/debug/start")
    public R<FlowDebugResponse> startDebug(@PathVariable Long id,
                                           @RequestBody(required = false) FlowDebugStartRequest request) {
        return R.ok(flowDebugService.start(id, request));
    }

    @PostMapping("/debug/{sessionId}/input")
    public R<FlowDebugResponse> debugInput(@PathVariable String sessionId,
                                           @Valid @RequestBody FlowDebugInputRequest request) {
        return R.ok(flowDebugService.input(sessionId, request));
    }

    @PostMapping("/{id}/offline")
    public R<Void> offline(@PathVariable Long id,
                           @AuthenticationPrincipal Long currentUserId) {
        flowStore.offline(id, currentUserId);
        return R.ok();
    }

    @PostMapping("/{id}/versions/{version}/restore-draft")
    public R<Void> restoreVersionToDraft(@PathVariable Long id,
                                         @PathVariable Integer version,
                                         @AuthenticationPrincipal Long currentUserId) {
        flowStore.restoreVersionToDraft(id, version, currentUserId);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id,
                          @AuthenticationPrincipal Long currentUserId) {
        flowStore.delete(id, currentUserId);
        return R.ok();
    }
}
