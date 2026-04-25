package com.ivr.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ivr.admin.entity.IvrFlowVersion;
import com.ivr.admin.mapper.IvrFlowVersionMapper;
import com.ivr.engine.graph.FlowGraph;
import com.ivr.engine.graph.FlowGraphParser;
import com.ivr.engine.graph.FlowGraphProvider;
import org.springframework.stereotype.Service;

/**
 * 从 {@code ivr_flow_version.graph_json} 加载流程图。
 *
 * <p>{@code version} 为 null / 0 时，回退到草稿版（version=0）；都没有时取最新版。
 */
@Service
public class MybatisFlowGraphProvider implements FlowGraphProvider {

    private final IvrFlowVersionMapper versionMapper;
    private final FlowGraphParser parser;

    public MybatisFlowGraphProvider(IvrFlowVersionMapper versionMapper, FlowGraphParser parser) {
        this.versionMapper = versionMapper;
        this.parser = parser;
    }

    @Override
    public FlowGraph load(Long flowId, Integer version) {
        if (flowId == null) {
            throw new IllegalArgumentException("flowId is required");
        }
        IvrFlowVersion record = null;
        if (version != null && version > 0) {
            record = versionMapper.selectOne(new LambdaQueryWrapper<IvrFlowVersion>()
                    .eq(IvrFlowVersion::getFlowId, flowId)
                    .eq(IvrFlowVersion::getVersion, version)
                    .last("LIMIT 1"));
        }
        if (record == null) {
            record = versionMapper.selectOne(new LambdaQueryWrapper<IvrFlowVersion>()
                    .eq(IvrFlowVersion::getFlowId, flowId)
                    .orderByDesc(IvrFlowVersion::getVersion)
                    .orderByDesc(IvrFlowVersion::getId)
                    .last("LIMIT 1"));
        }
        if (record == null) {
            throw new IllegalStateException("flow version not found: flowId=" + flowId + " version=" + version);
        }
        return parser.parse(record.getGraphJson());
    }
}
