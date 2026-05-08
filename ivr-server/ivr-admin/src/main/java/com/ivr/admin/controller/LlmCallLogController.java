package com.ivr.admin.controller;

import com.ivr.admin.service.LlmCallLogService;
import com.ivr.common.result.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/ai/llm/logs")
public class LlmCallLogController {

    private final LlmCallLogService service;

    public LlmCallLogController(LlmCallLogService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    public R<Map<String, Object>> overview() {
        return R.ok(service.overview());
    }

    @GetMapping("/page")
    public R<Map<String, Object>> page(@RequestParam(defaultValue = "1") int current,
                                       @RequestParam(defaultValue = "10") int size,
                                       @RequestParam(required = false) String scene,
                                       @RequestParam(required = false) String status,
                                       @RequestParam(required = false) String keyword) {
        return R.ok(service.page(current, size, scene, status, keyword));
    }

    @GetMapping("/scenes")
    public R<List<String>> scenes() {
        return R.ok(service.scenes());
    }
}
