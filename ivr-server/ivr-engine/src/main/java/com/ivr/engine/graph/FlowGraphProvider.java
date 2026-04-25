package com.ivr.engine.graph;

/**
 * 流程图加载抽象。engine 模块不直接依赖 ORM / mapper，由上层（admin）实现。
 *
 * <p>实现示例：从 {@code ivr_flow_version} 表按 {@code (flowId, version)} 取 {@code graph_json}
 * 并交给 {@link FlowGraphParser} 解析。生产可叠 Redis 缓存。
 */
public interface FlowGraphProvider {

    /**
     * 加载并解析流程图。
     *
     * @param flowId  流程主表 id
     * @param version 流程版本号；传 null 或 0 时由实现决定语义（一般取已发布最新版）
     * @return 解析后的 {@link FlowGraph}
     */
    FlowGraph load(Long flowId, Integer version);
}
