package com.ivr.admin.controller;

import com.ivr.admin.service.FlowStore;
import com.ivr.common.result.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    private final FlowStore flowStore;

    public DashboardController(FlowStore flowStore) {
        this.flowStore = flowStore;
    }

    @GetMapping("/overview")
    public R<Map<String, Object>> overview() {
        return R.ok(flowStore.dashboardStats());
    }
}
