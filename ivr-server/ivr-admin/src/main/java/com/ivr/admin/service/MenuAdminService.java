package com.ivr.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ivr.admin.dto.MenuTreeItem;
import com.ivr.admin.entity.SysMenu;
import com.ivr.admin.mapper.SysMenuMapper;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MenuAdminService {

    private final SysMenuMapper menuMapper;

    public MenuAdminService(SysMenuMapper menuMapper) {
        this.menuMapper = menuMapper;
    }

    public List<MenuTreeItem> tree() {
        List<MenuTreeItem> items = menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                        .orderByAsc(SysMenu::getSort)
                        .orderByAsc(SysMenu::getId))
                .stream()
                .map(this::toTreeItem)
                .toList();

        Map<Long, MenuTreeItem> byId = new LinkedHashMap<>();
        items.forEach(item -> byId.put(item.getId(), item));
        items.forEach(item -> {
            if (item.getParentId() != null && item.getParentId() != 0 && byId.containsKey(item.getParentId())) {
                byId.get(item.getParentId()).getChildren().add(item);
            }
        });
        byId.values().forEach(this::sortChildren);
        return byId.values().stream()
                .filter(item -> item.getParentId() == null || item.getParentId() == 0)
                .sorted(Comparator.comparing(MenuTreeItem::getSort, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(MenuTreeItem::getId))
                .toList();
    }

    private void sortChildren(MenuTreeItem item) {
        item.getChildren().sort(Comparator.comparing(MenuTreeItem::getSort, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(MenuTreeItem::getId));
        item.getChildren().forEach(this::sortChildren);
    }

    private MenuTreeItem toTreeItem(SysMenu menu) {
        MenuTreeItem item = new MenuTreeItem();
        item.setId(menu.getId());
        item.setParentId(menu.getParentId());
        item.setMenuName(menu.getMenuName());
        item.setMenuType(menu.getMenuType());
        item.setPath(menu.getPath());
        item.setComponent(menu.getComponent());
        item.setPerms(menu.getPerms());
        item.setIcon(menu.getIcon());
        item.setSort(menu.getSort());
        item.setVisible(menu.getVisible());
        item.setStatus(menu.getStatus());
        return item;
    }
}
