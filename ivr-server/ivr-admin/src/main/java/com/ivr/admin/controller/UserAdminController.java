package com.ivr.admin.controller;

import com.ivr.admin.dto.PageResult;
import com.ivr.admin.dto.ResetPasswordRequest;
import com.ivr.admin.dto.UserListItem;
import com.ivr.admin.dto.UserRolesRequest;
import com.ivr.admin.dto.UserStatusRequest;
import com.ivr.admin.service.UserAdminService;
import com.ivr.common.result.R;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/system/user")
public class UserAdminController {

    private final UserAdminService userAdminService;

    public UserAdminController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    @GetMapping("/page")
    public R<PageResult<UserListItem>> page(@RequestParam(defaultValue = "1") int current,
                                            @RequestParam(defaultValue = "10") int size,
                                            @RequestParam(required = false) String keyword) {
        return R.ok(userAdminService.page(current, size, keyword));
    }

    @PutMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable Long id,
                                @Valid @RequestBody UserStatusRequest request,
                                @AuthenticationPrincipal Long currentUserId) {
        userAdminService.updateStatus(id, request.getStatus(), currentUserId);
        return R.ok();
    }

    @PutMapping("/{id}/password")
    public R<Void> resetPassword(@PathVariable Long id,
                                 @Valid @RequestBody ResetPasswordRequest request) {
        userAdminService.resetPassword(id, request.getPassword());
        return R.ok();
    }

    @PutMapping("/{id}/roles")
    public R<Void> assignRoles(@PathVariable Long id,
                               @Valid @RequestBody UserRolesRequest request,
                               @AuthenticationPrincipal Long currentUserId) {
        userAdminService.assignRoles(id, request.getRoleIds(), currentUserId);
        return R.ok();
    }
}
