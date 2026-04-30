# RAG 知识库接入操作指南

这份文档按“先跑通，再理解”的顺序写。你可以先照着做，成功后再回头看原理。

## 1. 先理解 3 个词

- 知识库：一个资料分组，例如“售后FAQ”“套餐规则”“投诉处理规则”。
- 文档：你录入的一篇资料，例如一篇 FAQ 文本。
- 切片：系统把长文档切成很多小段，用户提问时先找最相关的小段，再交给大模型生成回答。

RAG 的完整流程是：

用户说话 -> ASR 转文字 -> RAG 检索知识切片 -> 大模型根据切片生成回答 -> TTS 播放回答。

## 2. 配置大模型

后端配置在 `ivr-server/ivr-admin/src/main/resources/application.yml`：

```yaml
spring:
  ai:
    openai:
      api-key: ${DASHSCOPE_API_KEY:sk-placeholder}
      base-url: ${LLM_BASE_URL:https://dashscope.aliyuncs.com/compatible-mode}
      chat:
        options:
          model: ${LLM_MODEL:qwen-plus}
      embedding:
        options:
          model: ${EMBED_MODEL:text-embedding-v2}
```

本地启动前设置环境变量：

```powershell
$env:DASHSCOPE_API_KEY="你的通义千问 DashScope Key"
$env:LLM_MODEL="qwen-plus"
$env:EMBED_MODEL="text-embedding-v2"
```

没有 Key 时，文档仍然会保存，RAG 会退回关键词检索；有 Key 时会额外保存向量，语义检索效果更好。

## 3. 新建知识库

1. 登录前端：`http://localhost:5173`
2. 左侧进入“知识库”
3. 点击“新建知识库”
4. 填写：
   - 知识库名称：例如 `售后FAQ`
   - 描述：例如 `退款、换货、投诉等常见问题`
   - Embedding 模型：先用默认 `text-embedding-v2`
5. 保存后记住列表里的 `ID`

## 4. 新建知识文档

1. 左侧进入“知识文档”
2. 点击“新建文档”
3. 选择刚才创建的知识库
4. 填标题和内容
5. 点击“保存并索引”

建议内容写成这种格式：

```text
退款规则：订单支付后 7 天内可申请退款，已发货订单需先拒收或退回商品。
换货规则：商品签收后 15 天内可申请换货，人为损坏不支持换货。
投诉处理：客户投诉会在 24 小时内创建工单，普通投诉 2 个工作日内回复。
人工服务：客户要求人工服务时，可以转接到 1000 坐席。
```

保存后看到状态为“已索引”，就说明文档已经可被 RAG 检索。

## 5. 在流程里使用 RAG

1. 进入“流程列表”
2. 新建或编辑一个流程
3. 拖入这些节点：
   - 开始
   - 播放语音
   - 语音识别
   - AI 问答
   - 播放语音
   - 转人工
   - 结束
4. 推荐连线：

```text
开始 -> 播放语音 -> 语音识别 -> AI 问答 -> 播放语音 -> 结束
AI 问答 -- fallback --> 转人工
```

5. 选中“AI 问答”节点，填写：
   - 知识库 ID：刚才知识库列表里的 ID
   - 检索片段数：`3`
   - 问题变量：`lastAsr`
   - 答案变量：`ragAnswer`
   - 未命中分支：`fallback`
6. 选中 RAG 后面的“播放语音”节点，播放文本填：

```text
${ragAnswer}
```

## 6. 调试方法

1. 在“流程列表”点击“调试”
2. 到语音识别节点时，输入一句模拟语音，例如：

```text
我想申请退款
```

3. 如果知识库命中，事件里会看到“知识库命中 N 段，已生成回答”
4. 如果未命中，会走 `fallback` 分支，通常接转人工

## 7. 这版 MVP 的边界

- 现在使用 MySQL 保存切片和向量，适合学习和小规模演示。
- 文档上传先做了文本粘贴，PDF、Word 解析可以后续加。
- 大规模生产建议把向量检索迁移到 Milvus、pgvector 或 Elasticsearch dense vector。
- 如果向量化接口失败，系统会自动使用关键词检索兜底。
