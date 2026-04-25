package com.ivr.admin.controller;

import com.ivr.admin.dto.MenuTreeItem;
import com.ivr.admin.service.MenuAdminService;
import com.ivr.common.result.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/system/menu")
public class MenuAdminController {

    private final MenuAdminService menuAdminService;

    public MenuAdminController(MenuAdminService menuAdminService) {
        this.menuAdminService = menuAdminService;
    }

    @GetMapping("/tree")
    public R<List<MenuTreeItem>> tree() {
        return R.ok(menuAdminService.tree());
    }
}
