package com.ivr.admin.controller;

import com.ivr.admin.service.OperationAuditService;
import com.ivr.common.result.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/system/audit")
public class OperationAuditController {

    private final OperationAuditService service;

    public OperationAuditController(OperationAuditService service) {
        this.service = service;
    }

    @GetMapping("/page")
    public R<Map<String, Object>> page(@RequestParam(defaultValue = "1") int current,
                                       @RequestParam(defaultValue = "10") int size,
                                       @RequestParam(required = false) String keyword,
                                       @RequestParam(required = false) String moduleName,
                                       @RequestParam(required = false) String status) {
        return R.ok(service.page(current, size, keyword, moduleName, status));
    }

    @GetMapping("/modules")
    public R<List<String>> modules() {
        return R.ok(service.modules());
    }
}
