-- =========================================================================
--  操作审计模块增量 SQL
--  适用于已经初始化过的数据库：补表 + 补菜单权限
-- =========================================================================

USE ivr;

CREATE TABLE IF NOT EXISTS operation_audit_log (
  id             BIGINT        PRIMARY KEY AUTO_INCREMENT,
  user_id        BIGINT,
  username       VARCHAR(64),
  nickname       VARCHAR(64),
  module_name    VARCHAR(64)   NOT NULL COMMENT '业务模块',
  operation_type VARCHAR(64)   NOT NULL COMMENT 'create/update/delete/publish/offline',
  operation_name VARCHAR(128)  NOT NULL COMMENT '展示名称',
  request_method VARCHAR(16)   NOT NULL,
  request_uri    VARCHAR(255)  NOT NULL,
  query_params   VARCHAR(2000),
  request_body   TEXT,
  ip             VARCHAR(64),
  user_agent     VARCHAR(500),
  status         VARCHAR(16)   NOT NULL COMMENT 'success/failed',
  result_code    INT,
  error_message  VARCHAR(2000),
  latency_ms     BIGINT        DEFAULT 0,
  created_at     DATETIME      DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_created(created_at),
  INDEX idx_user_created(user_id, created_at),
  INDEX idx_module_created(module_name, created_at),
  INDEX idx_status_created(status, created_at)
) ENGINE=InnoDB COMMENT='后台操作审计日志';

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
SELECT 504, 500, '操作审计', 2, 'audit', 'system/OperationAudit', 'system:audit:list', 'Tickets', 4, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 504)
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'system:audit:list');

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, 504
WHERE EXISTS (SELECT 1 FROM sys_role WHERE id = 1)
  AND EXISTS (SELECT 1 FROM sys_menu WHERE id = 504)
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu WHERE role_id = 1 AND menu_id = 504
  );
