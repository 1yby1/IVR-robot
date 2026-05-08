package com.ivr.admin.controller;

import com.ivr.admin.dto.HotlineListItem;
import com.ivr.admin.dto.HotlineImpactResponse;
import com.ivr.admin.dto.HotlineRequest;
import com.ivr.admin.dto.HotlineStatusRequest;
import com.ivr.admin.dto.PageResult;
import com.ivr.admin.service.HotlineAdminService;
import com.ivr.common.result.R;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/robot/hotline")
public class HotlineAdminController {

    private final HotlineAdminService hotlineAdminService;

    public HotlineAdminController(HotlineAdminService hotlineAdminService) {
        this.hotlineAdminService = hotlineAdminService;
    }

    @GetMapping("/page")
    public R<PageResult<HotlineListItem>> page(@RequestParam(defaultValue = "1") int current,
                                               @RequestParam(defaultValue = "10") int size,
                                               @RequestParam(required = false) String keyword) {
        return R.ok(hotlineAdminService.page(current, size, keyword));
    }

    @GetMapping("/flow/{flowId}/impact")
    public R<HotlineImpactResponse> flowImpact(@PathVariable Long flowId) {
        return R.ok(hotlineAdminService.flowImpact(flowId));
    }

    @PostMapping
    public R<Long> create(@Valid @RequestBody HotlineRequest request,
                          @AuthenticationPrincipal Long currentUserId) {
        return R.ok(hotlineAdminService.create(
                request.getHotline(),
                request.getFlowId(),
                request.getRemark(),
                currentUserId
        ));
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody HotlineRequest request) {
        hotlineAdminService.update(id, request.getHotline(), request.getFlowId(), request.getRemark());
        return R.ok();
    }

    @PutMapping("/{id}/enabled")
    public R<Void> updateEnabled(@PathVariable Long id, @Valid @RequestBody HotlineStatusRequest request) {
        hotlineAdminService.updateEnabled(id, request.getEnabled());
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        hotlineAdminService.delete(id);
        return R.ok();
    }
}
