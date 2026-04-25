package com.ivr.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ivr.admin.dto.HotlineListItem;
import com.ivr.admin.dto.PageResult;
import com.ivr.admin.entity.IvrFlow;
import com.ivr.admin.entity.IvrHotline;
import com.ivr.admin.mapper.IvrFlowMapper;
import com.ivr.admin.mapper.IvrHotlineMapper;
import com.ivr.common.exception.BusinessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class HotlineAdminService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final IvrHotlineMapper hotlineMapper;
    private final IvrFlowMapper flowMapper;

    public HotlineAdminService(IvrHotlineMapper hotlineMapper, IvrFlowMapper flowMapper) {
        this.hotlineMapper = hotlineMapper;
        this.flowMapper = flowMapper;
    }

    public PageResult<HotlineListItem> page(int current, int size, String keyword) {
        int safeCurrent = Math.max(current, 1);
        int safeSize = Math.max(size, 1);
        String normalizedKeyword = keyword == null ? "" : keyword.trim();

        LambdaQueryWrapper<IvrHotline> wrapper = new LambdaQueryWrapper<IvrHotline>()
                .orderByDesc(IvrHotline::getUpdatedAt)
                .orderByDesc(IvrHotline::getId);
        if (StringUtils.hasText(normalizedKeyword)) {
            wrapper.and(w -> w.like(IvrHotline::getHotline, normalizedKeyword)
                    .or()
                    .like(IvrHotline::getRemark, normalizedKeyword));
        }

        Page<IvrHotline> page = hotlineMapper.selectPage(Page.of(safeCurrent, safeSize), wrapper);
        Map<Long, IvrFlow> flows = loadFlows(page.getRecords().stream().map(IvrHotline::getFlowId).toList());

        PageResult<HotlineListItem> result = new PageResult<>();
        result.setRecords(page.getRecords().stream().map(item -> toListItem(item, flows.get(item.getFlowId()))).toList());
        result.setTotal(page.getTotal());
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(String hotline, Long flowId, String remark, Long currentUserId) {
        IvrFlow flow = getPublishedFlow(flowId);
        IvrHotline entity = new IvrHotline();
        entity.setHotline(normalizeHotline(hotline));
        entity.setFlowId(flow.getId());
        entity.setEnabled(1);
        entity.setRemark(normalizeText(remark));
        entity.setCreatedBy(currentUserId);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(entity.getCreatedAt());
        try {
            hotlineMapper.insert(entity);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(400, "热线号码已存在");
        }
        return entity.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, String hotline, Long flowId, String remark) {
        IvrHotline entity = getRequired(id);
        IvrFlow flow = getPublishedFlow(flowId);
        entity.setHotline(normalizeHotline(hotline));
        entity.setFlowId(flow.getId());
        entity.setRemark(normalizeText(remark));
        entity.setUpdatedAt(LocalDateTime.now());
        try {
            hotlineMapper.updateById(entity);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(400, "热线号码已存在");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateEnabled(Long id, Integer enabled) {
        if (!Objects.equals(enabled, 0) && !Objects.equals(enabled, 1)) {
            throw new BusinessException(400, "启用状态不正确");
        }
        IvrHotline entity = getRequired(id);
        entity.setEnabled(enabled);
        entity.setUpdatedAt(LocalDateTime.now());
        hotlineMapper.updateById(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (hotlineMapper.deleteById(id) == 0) {
            throw new BusinessException(404, "热线不存在");
        }
    }

    private IvrHotline getRequired(Long id) {
        IvrHotline entity = hotlineMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(404, "热线不存在");
        }
        return entity;
    }

    private IvrFlow getPublishedFlow(Long flowId) {
        IvrFlow flow = flowMapper.selectById(flowId);
        if (flow == null || Objects.equals(flow.getDeleted(), 1)) {
            throw new BusinessException(400, "绑定流程不存在");
        }
        if (!Objects.equals(flow.getStatus(), 1)) {
            throw new BusinessException(400, "只能绑定已发布流程");
        }
        return flow;
    }

    private Map<Long, IvrFlow> loadFlows(List<Long> flowIds) {
        List<Long> ids = flowIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return flowMapper.selectBatchIds(ids).stream().collect(Collectors.toMap(IvrFlow::getId, Function.identity()));
    }

    private HotlineListItem toListItem(IvrHotline hotline, IvrFlow flow) {
        HotlineListItem item = new HotlineListItem();
        item.setId(hotline.getId());
        item.setHotline(hotline.getHotline());
        item.setFlowId(hotline.getFlowId());
        item.setFlowCode(flow == null ? "" : flow.getFlowCode());
        item.setFlowName(flow == null ? "流程不存在" : flow.getFlowName());
        item.setFlowVersion(flow == null ? 0 : flow.getCurrentVersion());
        item.setEnabled(hotline.getEnabled());
        item.setRemark(hotline.getRemark());
        item.setCreatedAt(formatTime(hotline.getCreatedAt()));
        item.setUpdatedAt(formatTime(hotline.getUpdatedAt()));
        return item;
    }

    private String normalizeHotline(String hotline) {
        return hotline == null ? "" : hotline.trim();
    }

    private String normalizeText(String text) {
        return StringUtils.hasText(text) ? text.trim() : "";
    }

    private String formatTime(LocalDateTime time) {
        return time == null ? "" : TIME_FMT.format(time);
    }
}
