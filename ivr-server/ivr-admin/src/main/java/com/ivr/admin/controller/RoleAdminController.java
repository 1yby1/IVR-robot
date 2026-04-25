package com.ivr.admin.controller;

import com.ivr.admin.dto.PageResult;
import com.ivr.admin.dto.RoleListItem;
import com.ivr.admin.dto.RoleMenusRequest;
import com.ivr.admin.dto.RoleStatusRequest;
import com.ivr.admin.service.RoleAdminService;
import com.ivr.common.result.R;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/system/role")
public class RoleAdminController {

    private final RoleAdminService roleAdminService;

    public RoleAdminController(RoleAdminService roleAdminService) {
        this.roleAdminService = roleAdminService;
    }

    @GetMapping("/page")
    public R<PageResult<RoleListItem>> page(@RequestParam(defaultValue = "1") int current,
                                            @RequestParam(defaultValue = "10") int size,
                                            @RequestParam(required = false) String keyword) {
        return R.ok(roleAdminService.page(current, size, keyword));
    }

    @GetMapping("/enabled")
    public R<List<RoleListItem>> listEnabled() {
        return R.ok(roleAdminService.listEnabled());
    }

    @PutMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable Long id,
                                @Valid @RequestBody RoleStatusRequest request) {
        roleAdminService.updateStatus(id, request.getStatus());
        return R.ok();
    }

    @GetMapping("/{id}/menus")
    public R<List<Long>> getMenuIds(@PathVariable Long id) {
        return R.ok(roleAdminService.getMenuIds(id));
    }

    @PutMapping("/{id}/menus")
    public R<Void> assignMenus(@PathVariable Long id,
                               @Valid @RequestBody RoleMenusRequest request) {
        roleAdminService.assignMenus(id, request.getMenuIds());
        return R.ok();
    }
}
