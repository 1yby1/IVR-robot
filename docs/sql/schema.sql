-- =========================================================================
--  IVR 智能语音机器人 - 数据库建表脚本
--  MySQL 8.x  utf8mb4
-- =========================================================================

CREATE DATABASE IF NOT EXISTS ivr DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE ivr;

-- ==============================
-- 权限体系（RBAC）
-- ==============================

DROP TABLE IF EXISTS sys_user;
CREATE TABLE sys_user (
  id            BIGINT       PRIMARY KEY AUTO_INCREMENT,
  username      VARCHAR(64)  NOT NULL COMMENT '登录名',
  password      VARCHAR(128) NOT NULL COMMENT 'BCrypt 加密',
  nickname      VARCHAR(64)              COMMENT '昵称',
  phone         VARCHAR(20),
  email         VARCHAR(128),
  avatar        VARCHAR(500),
  status        TINYINT      DEFAULT 1   COMMENT '1启用 0禁用',
  last_login_at DATETIME,
  last_login_ip VARCHAR(64),
  remark        VARCHAR(255),
  created_by    BIGINT,
  created_at    DATETIME     DEFAULT CURRENT_TIMESTAMP,
  updated_by    BIGINT,
  updated_at    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted       TINYINT      DEFAULT 0,
  UNIQUE KEY uk_username(username)
) ENGINE=InnoDB COMMENT='系统用户';

DROP TABLE IF EXISTS sys_role;
CREATE TABLE sys_role (
  id         BIGINT      PRIMARY KEY AUTO_INCREMENT,
  role_code  VARCHAR(64) NOT NULL COMMENT '角色编码',
  role_name  VARCHAR(64) NOT NULL COMMENT '角色名',
  data_scope TINYINT     DEFAULT 1 COMMENT '1全部 2本人 3自定义',
  sort       INT         DEFAULT 0,
  status     TINYINT     DEFAULT 1,
  remark     VARCHAR(255),
  created_at DATETIME    DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted    TINYINT     DEFAULT 0,
  UNIQUE KEY uk_role_code(role_code)
) ENGINE=InnoDB COMMENT='系统角色';

DROP TABLE IF EXISTS sys_menu;
CREATE TABLE sys_menu (
  id         BIGINT       PRIMARY KEY AUTO_INCREMENT,
  parent_id  BIGINT       DEFAULT 0,
  menu_name  VARCHAR(64)  NOT NULL,
  menu_type  TINYINT      NOT NULL COMMENT '1目录 2菜单 3按钮',
  path       VARCHAR(255),
  component  VARCHAR(255),
  perms      VARCHAR(128),
  icon       VARCHAR(64),
  sort       INT          DEFAULT 0,
  visible    TINYINT      DEFAULT 1,
  status     TINYINT      DEFAULT 1,
  created_at DATETIME     DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_parent(parent_id)
) ENGINE=InnoDB COMMENT='菜单/权限';

DROP TABLE IF EXISTS sys_user_role;
CREATE TABLE sys_user_role (
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  PRIMARY KEY(user_id, role_id)
) ENGINE=InnoDB COMMENT='用户-角色关联';

DROP TABLE IF EXISTS sys_role_menu;
CREATE TABLE sys_role_menu (
  role_id BIGINT NOT NULL,
  menu_id BIGINT NOT NULL,
  PRIMARY KEY(role_id, menu_id)
) ENGINE=InnoDB COMMENT='角色-菜单关联';

-- ==============================
-- IVR 流程
-- ==============================

DROP TABLE IF EXISTS ivr_flow;
CREATE TABLE ivr_flow (
  id              BIGINT       PRIMARY KEY AUTO_INCREMENT,
  flow_code       VARCHAR(64)  NOT NULL COMMENT '流程编码',
  flow_name       VARCHAR(128) NOT NULL,
  description     VARCHAR(500),
  status          TINYINT      DEFAULT 0 COMMENT '0草稿 1已发布 2已下线',
  current_version INT          DEFAULT 0 COMMENT '当前发布版本',
  created_by      BIGINT,
  created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
  updated_by      BIGINT,
  updated_at      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted         TINYINT      DEFAULT 0,
  UNIQUE KEY uk_flow_code(flow_code),
  INDEX idx_status(status)
) ENGINE=InnoDB COMMENT='IVR 流程主表';

DROP TABLE IF EXISTS ivr_flow_version;
CREATE TABLE ivr_flow_version (
  id          BIGINT    PRIMARY KEY AUTO_INCREMENT,
  flow_id     BIGINT    NOT NULL,
  version     INT       NOT NULL,
  graph_json  LONGTEXT  NOT NULL COMMENT 'LogicFlow 完整图数据',
  change_note VARCHAR(255),
  published   TINYINT   DEFAULT 0,
  created_by  BIGINT,
  created_at  DATETIME  DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_flow_ver(flow_id, version),
  INDEX idx_flow(flow_id)
) ENGINE=InnoDB COMMENT='流程版本';

DROP TABLE IF EXISTS ivr_flow_node;
CREATE TABLE ivr_flow_node (
  id         BIGINT       PRIMARY KEY AUTO_INCREMENT,
  flow_id    BIGINT       NOT NULL,
  version    INT          NOT NULL,
  node_key   VARCHAR(64)  NOT NULL COMMENT 'LogicFlow node id',
  node_type  VARCHAR(32)  NOT NULL,
  node_name  VARCHAR(128),
  config     JSON,
  INDEX idx_flow_ver(flow_id, version)
) ENGINE=InnoDB COMMENT='流程节点（用于快速查询）';

DROP TABLE IF EXISTS ivr_flow_edge;
CREATE TABLE ivr_flow_edge (
  id          BIGINT      PRIMARY KEY AUTO_INCREMENT,
  flow_id     BIGINT      NOT NULL,
  version     INT         NOT NULL,
  edge_key    VARCHAR(64),
  source_key  VARCHAR(64) NOT NULL,
  target_key  VARCHAR(64) NOT NULL,
  `condition` VARCHAR(255) COMMENT '边条件，如 dtmf==1',
  INDEX idx_flow_ver(flow_id, version)
) ENGINE=InnoDB COMMENT='流程连线';

-- ==============================
-- 语音资源 & 热线绑定
-- ==============================

DROP TABLE IF EXISTS ivr_voice;
CREATE TABLE ivr_voice (
  id         BIGINT       PRIMARY KEY AUTO_INCREMENT,
  voice_name VARCHAR(128) NOT NULL,
  voice_type TINYINT      NOT NULL COMMENT '1上传音频 2TTS生成',
  text       TEXT,
  tts_voice  VARCHAR(64)  COMMENT 'TTS 音色',
  file_path  VARCHAR(500),
  duration   INT,
  remark     VARCHAR(255),
  created_by BIGINT,
  created_at DATETIME     DEFAULT CURRENT_TIMESTAMP,
  deleted    TINYINT      DEFAULT 0
) ENGINE=InnoDB COMMENT='语音资源库';

DROP TABLE IF EXISTS ivr_hotline;
CREATE TABLE ivr_hotline (
  id         BIGINT      PRIMARY KEY AUTO_INCREMENT,
  hotline    VARCHAR(32) NOT NULL,
  flow_id    BIGINT      NOT NULL,
  enabled    TINYINT     DEFAULT 1,
  remark     VARCHAR(255),
  created_by BIGINT,
  created_at DATETIME    DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_hotline(hotline)
) ENGINE=InnoDB COMMENT='热线号码绑定';

-- ==============================
-- 知识库（RAG）
-- ==============================

DROP TABLE IF EXISTS kb_base;
CREATE TABLE kb_base (
  id         BIGINT       PRIMARY KEY AUTO_INCREMENT,
  kb_name    VARCHAR(128) NOT NULL,
  description VARCHAR(500),
  embedding_model VARCHAR(64) DEFAULT 'text-embedding-v2',
  created_at DATETIME     DEFAULT CURRENT_TIMESTAMP,
  deleted    TINYINT      DEFAULT 0
) ENGINE=InnoDB COMMENT='知识库分组';

DROP TABLE IF EXISTS kb_doc;
CREATE TABLE kb_doc (
  id           BIGINT       PRIMARY KEY AUTO_INCREMENT,
  kb_id        BIGINT       NOT NULL,
  title        VARCHAR(255) NOT NULL,
  content      LONGTEXT,
  source_file  VARCHAR(500),
  file_type    VARCHAR(16)  COMMENT 'md/txt/pdf/docx',
  status       TINYINT      DEFAULT 0 COMMENT '0待向量化 1向量化中 2已完成 3失败',
  created_at   DATETIME     DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_kb(kb_id)
) ENGINE=InnoDB COMMENT='知识文档';

DROP TABLE IF EXISTS kb_chunk;
CREATE TABLE kb_chunk (
  id         BIGINT   PRIMARY KEY AUTO_INCREMENT,
  doc_id     BIGINT   NOT NULL,
  kb_id      BIGINT   NOT NULL,
  chunk_idx  INT,
  content    TEXT,
  embedding  JSON     COMMENT '向量（MVP 用 JSON，生产迁 Milvus）',
  token_cnt  INT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_doc(doc_id),
  INDEX idx_kb(kb_id)
) ENGINE=InnoDB COMMENT='知识切片';

-- ==============================
-- 通话数据
-- ==============================

DROP TABLE IF EXISTS call_log;
CREATE TABLE call_log (
  id              BIGINT       PRIMARY KEY AUTO_INCREMENT,
  call_uuid       VARCHAR(64)  NOT NULL,
  caller          VARCHAR(32),
  callee          VARCHAR(32),
  flow_id         BIGINT,
  flow_version    INT,
  start_time      DATETIME,
  answer_time     DATETIME,
  end_time        DATETIME,
  duration        INT          COMMENT '通话秒数',
  end_reason      VARCHAR(64)  COMMENT 'normal/transfer/timeout/error',
  transfer_to     VARCHAR(32),
  hangup_by       VARCHAR(16),
  ai_hit          TINYINT      DEFAULT 0 COMMENT 'AI 是否解决',
  satisfaction    TINYINT      COMMENT '满意度 1-5',
  UNIQUE KEY uk_call_uuid(call_uuid),
  INDEX idx_callee_time(callee, start_time),
  INDEX idx_flow(flow_id)
) ENGINE=InnoDB COMMENT='通话记录主表';

DROP TABLE IF EXISTS call_event;
CREATE TABLE call_event (
  id         BIGINT       PRIMARY KEY AUTO_INCREMENT,
  call_uuid  VARCHAR(64)  NOT NULL,
  node_key   VARCHAR(64),
  node_type  VARCHAR(32),
  event_type VARCHAR(32)  COMMENT 'enter/exit/dtmf/asr/ai/error',
  payload    JSON,
  event_time DATETIME(3)  DEFAULT CURRENT_TIMESTAMP(3),
  INDEX idx_uuid(call_uuid),
  INDEX idx_time(event_time)
) ENGINE=InnoDB COMMENT='通话节点事件轨迹';

DROP TABLE IF EXISTS call_voicemail;
CREATE TABLE call_voicemail (
  id         BIGINT       PRIMARY KEY AUTO_INCREMENT,
  call_uuid  VARCHAR(64),
  caller     VARCHAR(32),
  callee     VARCHAR(32),
  file_path  VARCHAR(500),
  duration   INT,
  transcript TEXT         COMMENT 'ASR 转写',
  handled    TINYINT      DEFAULT 0,
  handled_by BIGINT,
  handled_at DATETIME,
  created_at DATETIME     DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_handled(handled),
  INDEX idx_created(created_at)
) ENGINE=InnoDB COMMENT='留言记录';

-- ==============================
-- 任务 / 定时作业
-- ==============================

DROP TABLE IF EXISTS task_def;
CREATE TABLE task_def (
  id         BIGINT       PRIMARY KEY AUTO_INCREMENT,
  task_name  VARCHAR(128) NOT NULL,
  task_type  VARCHAR(32)  COMMENT 'outbound/vectorize/cleanup',
  cron_expr  VARCHAR(64),
  params     JSON,
  enabled    TINYINT      DEFAULT 1,
  created_at DATETIME     DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB COMMENT='任务定义';

DROP TABLE IF EXISTS task_record;
CREATE TABLE task_record (
  id         BIGINT       PRIMARY KEY AUTO_INCREMENT,
  task_id    BIGINT       NOT NULL,
  status     VARCHAR(16)  COMMENT 'pending/running/success/failed',
  start_time DATETIME,
  end_time   DATETIME,
  result     TEXT,
  INDEX idx_task(task_id)
) ENGINE=InnoDB COMMENT='任务执行记录';
