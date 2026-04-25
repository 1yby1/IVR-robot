package com.ivr.admin.controller;

import com.ivr.admin.dto.FlowDebugInputRequest;
import com.ivr.admin.dto.FlowDebugResponse;
import com.ivr.admin.dto.FlowDebugStartRequest;
import com.ivr.admin.dto.FlowOptionItem;
import com.ivr.admin.service.FlowDebugService;
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

    public FlowController(FlowStore flowStore, FlowDebugService flowDebugService) {
        this.flowStore = flowStore;
        this.flowDebugService = flowDebugService;
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

    @GetMapping("/published-options")
    public R<List<FlowOptionItem>> publishedOptions() {
        return R.ok(flowStore.publishedOptions());
    }

    @PostMapping
    public R<Long> create(@RequestBody Map<String, Object> body,
                          @AuthenticationPrincipal Long currentUserId) {
        return R.ok(flowStore.create(body, currentUserId));
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

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id,
                          @AuthenticationPrincipal Long currentUserId) {
        flowStore.delete(id, currentUserId);
        return R.ok();
    }
}
