# IVR 智能语音机器人

> 面向客服场景的 7×24 小时自动化语音应答系统，支持可视化拖拽编排 IVR 流程 + AI 大模型能力（意图识别 / RAG 知识库 / TTS / ASR）。

## 技术栈

| 层 | 选型 |
|----|------|
| 前端 | Vue 3 + Vite + TypeScript + Element Plus + LogicFlow + Pinia |
| 后端 | Spring Boot 3.3 + Spring AI 1.0 + MyBatis-Plus + Spring Security |
| 数据库 | MySQL 8.x + Redis 7 |
| 呼叫平台 | FreeSWITCH（ESL 对接） |
| 大模型 | 国产通义千问 / DeepSeek（OpenAI 兼容协议） |
| 语音 | 阿里云 / 讯飞 ASR + TTS |
| 部署 | Docker Compose |

## 目录结构

```
IVR/
├── ivr-web/                # Vue 3 前端（管理后台 + 流程画布）
├── ivr-server/             # Spring Boot 多模块后端
│   ├── ivr-common/         # 公共模块（DTO / 工具 / 异常 / 安全）
│   ├── ivr-admin/          # 管理 REST API（Web 入口）
│   ├── ivr-engine/         # 流程引擎（节点执行器）
│   ├── ivr-ai/             # AI 能力（Spring AI + RAG + TTS/ASR）
│   └── ivr-call/           # FreeSWITCH ESL 对接
├── docker/                 # 部署编排
│   ├── docker-compose.yml
│   ├── freeswitch/
│   ├── mysql/
│   ├── redis/
│   └── nginx/
└── docs/
    └── sql/                # 建表脚本
```

## 快速开始

### 1. 环境要求
- Node.js 20+ + pnpm 8+（或 npm）
- JDK 17 或 21 + Maven 3.8+
- Docker Desktop（跑 FreeSWITCH / MySQL / Redis）

### 2. 起依赖服务
```bash
cd docker
docker compose up -d mysql redis freeswitch
```

### 3. 启动后端
```bash
cd ivr-server
mvn clean install -DskipTests
mvn -pl ivr-admin spring-boot:run
```

Windows 下如果项目路径包含中文，`spring-boot:run` 可能因为 classpath 编码找不到主类。可改用：

```powershell
cd ivr-server
mvn clean install -DskipTests
cd ivr-admin

```


### 4. 启动前端
```bash
cd ivr-web
pnpm install
pnpm dev
```

访问 `http://localhost:5173`，默认账号 `admin / admin123`。

## 分阶段路线图
详见 `docs/ROADMAP.md`（6 个 Sprint，约 6-9 周）。

## 文档
- 架构方案：`C:\Users\yby\.claude\plans\vectorized-squishing-melody.md`
- 需求文档：`IVR机器人项目需求.docx`
- RAG 操作指南：`docs/rag-quickstart.md`
