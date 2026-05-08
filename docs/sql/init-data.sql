-- =========================================================================
--  IVR 系统初始数据
--  默认账号: admin / admin123（BCrypt 加密）
-- =========================================================================

USE ivr;

-- ==============================
-- 角色
-- ==============================
INSERT INTO sys_role (id, role_code, role_name, data_scope, sort, remark) VALUES
(1, 'admin',    '超级管理员', 1, 1, '拥有所有权限'),
(2, 'operator', '运营人员',   1, 2, '可管理流程、语音、知识库'),
(3, 'viewer',   '只读人员',   2, 3, '仅查看通话与报表');

-- ==============================
-- 用户（admin / admin123，BCrypt 加密）
-- ==============================
INSERT INTO sys_user (id, username, password, nickname, status, remark) VALUES
(1, 'admin', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '超管', 1, '系统内置管理员');

INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 1);

-- ==============================
-- 菜单（目录 + 菜单 + 按钮）
-- ==============================
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort) VALUES
-- 一级目录
(100, 0,   '智能语音机器人', 1, '/robot',   NULL, NULL,                   'Phone',    10),
(200, 0,   '流程编排',       1, '/flow',    NULL, NULL,                   'Connection', 20),
(300, 0,   '知识库',         1, '/knowledge',NULL,NULL,                   'Document', 30),
(400, 0,   '数据统计',       1, '/report',  NULL, NULL,                   'TrendCharts', 40),
(500, 0,   '系统管理',       1, '/system',  NULL, NULL,                   'Setting',  99),

-- 智能语音机器人
(101, 100, '首页',           2, 'home',      'robot/Home',            'robot:home:view',        'House',    1),
(102, 100, '热线管理',       2, 'hotline',   'robot/Hotline',         'robot:hotline:list',     'PhoneFilled', 2),
(103, 100, '语音资源',       2, 'voice',     'robot/VoiceLibrary',    'robot:voice:list',       'Microphone', 3),
(104, 100, '通话记录',       2, 'callLogs',  'robot/CallLogs',        'robot:call:list',        'Tickets',  4),
(105, 100, '留言管理',       2, 'voicemail', 'robot/Voicemail',       'robot:voicemail:list',   'Message',  5),

-- 流程编排
(201, 200, '流程列表',       2, 'list',      'flow/FlowList',         'flow:list',              'List',     1),
(202, 200, '流程编辑器',     2, 'editor/:id','flow/FlowEditor',       'flow:edit',              'EditPen',  2),
(210, 201, '新增流程',       3, NULL,        NULL,                    'flow:add',               NULL,       1),
(211, 201, '删除流程',       3, NULL,        NULL,                    'flow:delete',            NULL,       2),
(212, 201, '发布流程',       3, NULL,        NULL,                    'flow:publish',           NULL,       3),

-- 知识库
(301, 300, '知识库管理',     2, 'base',      'knowledge/KbBase',      'kb:base:list',           'FolderOpened', 1),
(302, 300, '文档管理',       2, 'doc',       'knowledge/KbDoc',       'kb:doc:list',            'Document', 2),
(310, 301, '新建知识库',     3, NULL,        NULL,                    'kb:base:add',            NULL,       1),
(311, 301, '编辑知识库',     3, NULL,        NULL,                    'kb:base:edit',           NULL,       2),
(312, 301, '删除知识库',     3, NULL,        NULL,                    'kb:base:delete',         NULL,       3),
(320, 302, '新建文档',       3, NULL,        NULL,                    'kb:doc:add',             NULL,       1),
(321, 302, '编辑文档',       3, NULL,        NULL,                    'kb:doc:edit',            NULL,       2),
(322, 302, '删除文档',       3, NULL,        NULL,                    'kb:doc:delete',          NULL,       3),
(323, 302, '重建索引',       3, NULL,        NULL,                    'kb:doc:reindex',         NULL,       4),

-- 数据统计
(401, 400, '运营看板',       2, 'dashboard', 'report/Dashboard',      'report:dashboard',       'DataAnalysis', 1),
(402, 400, '节点热力',       2, 'heatmap',   'report/NodeHeatmap',    'report:heatmap',         'Histogram', 2),

-- 系统管理
(501, 500, '用户管理',       2, 'user',      'system/UserManage',     'system:user:list',       'User',     1),
(502, 500, '角色管理',       2, 'role',      'system/RoleManage',     'system:role:list',       'UserFilled', 2),
(503, 500, '菜单管理',       2, 'menu',      'system/MenuManage',     'system:menu:list',       'Menu',     3),
(504, 500, '操作审计',       2, 'audit',     'system/OperationAudit', 'system:audit:list',      'Tickets',  4);

-- admin 角色 => 所有菜单
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu;

-- operator 角色 => 首页 + 流程编排
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(2, 100),
(2, 101),
(2, 102),
(2, 104),
(2, 200),
(2, 201),
(2, 202),
(2, 210),
(2, 211),
(2, 212),
(2, 300),
(2, 301),
(2, 302),
(2, 310),
(2, 311),
(2, 312),
(2, 320),
(2, 321),
(2, 322),
(2, 323);

-- viewer 角色 => 首页
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(3, 100),
(3, 101),
(3, 104);

-- ==============================
-- 示例流程（欢迎 → 按键 → 结束）
-- ==============================
INSERT INTO ivr_flow (id, flow_code, flow_name, description, status, current_version, created_by) VALUES
(1, 'demo-welcome', '示例 - 欢迎流程', '按 1 听天气，按 2 听新闻', 1, 1, 1);

INSERT INTO ivr_flow_version (flow_id, version, graph_json, change_note, published, created_by) VALUES
(1, 1, '{"nodes":[{"id":"start","type":"start","x":100,"y":200,"properties":{}},{"id":"play1","type":"play","x":300,"y":200,"properties":{"tts":{"text":"欢迎致电，按1听天气，按2听新闻"}}},{"id":"dtmf1","type":"dtmf","x":500,"y":200,"properties":{"maxDigits":1,"timeoutSec":5,"mappings":[{"key":"1","nextNode":"play2"},{"key":"2","nextNode":"play3"}]}},{"id":"play2","type":"play","x":700,"y":100,"properties":{"tts":{"text":"今天天气晴朗"}}},{"id":"play3","type":"play","x":700,"y":300,"properties":{"tts":{"text":"最新新闻头条"}}},{"id":"end","type":"end","x":900,"y":200,"properties":{}}],"edges":[{"id":"e1","sourceNodeId":"start","targetNodeId":"play1"},{"id":"e2","sourceNodeId":"play1","targetNodeId":"dtmf1"},{"id":"e3","sourceNodeId":"dtmf1","targetNodeId":"play2"},{"id":"e4","sourceNodeId":"dtmf1","targetNodeId":"play3"},{"id":"e5","sourceNodeId":"play2","targetNodeId":"end"},{"id":"e6","sourceNodeId":"play3","targetNodeId":"end"}]}',
'初始发布', 1, 1);

-- 绑定测试热线
INSERT INTO ivr_hotline (hotline, flow_id, enabled, remark) VALUES ('4001', 1, 1, '示例热线');

-- 默认知识库 + 示例 FAQ 文档（含切片，无 embedding，关键词检索可直接命中）
INSERT INTO kb_base (id, kb_name, description) VALUES (1, '通用FAQ', '客户常见问题解答');

INSERT INTO ivr.kb_doc (id, kb_id, title, content, source_file, file_type, status) VALUES
(1, 1, '通用客服FAQ',
 '营业时间：我们的客服热线 7x24 小时不间断服务。人工坐席工作时间为每天 9:00 至 21:00。\n查询账单：您可以在 App 或官网登录后，从「我的-账单」入口查看最近 12 个月的账单明细，也可拨打 95XXX 让客服代查。\n投诉建议：对服务不满意可在通话中按 0 转人工，或通过官网「联系我们」提交工单，我们会在 24 小时内回复。\n套餐变更：套餐变更需在每月 1-25 日发起，次月 1 日生效；如需立即生效请联系客服办理。',
 'system-seed', 'txt', 2);

INSERT INTO ivr.kb_chunk (doc_id, kb_id, chunk_idx, content, token_cnt) VALUES
(1, 1, 0, '营业时间：我们的客服热线 7x24 小时不间断服务。人工坐席工作时间为每天 9:00 至 21:00。', 25),
(1, 1, 1, '查询账单：您可以在 App 或官网登录后，从「我的-账单」入口查看最近 12 个月的账单明细，也可拨打 95XXX 让客服代查。', 36),
(1, 1, 2, '投诉建议：对服务不满意可在通话中按 0 转人工，或通过官网「联系我们」提交工单，我们会在 24 小时内回复。', 28),
(1, 1, 3, '套餐变更：套餐变更需在每月 1-25 日发起，次月 1 日生效；如需立即生效请联系客服办理。', 22);
