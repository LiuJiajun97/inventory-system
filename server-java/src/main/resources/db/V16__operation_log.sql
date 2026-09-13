-- V16: 操作日志(审计日志)
-- 1) 新表 operation_log:记录全部写操作(POST/PUT/DELETE)的执行流水,
--    由 OperationLogAspect 切面落库,供 admin 审计"谁在何时对什么做了什么、成败与否"
--    注:主键用 BIGSERIAL——纯日志表量大,与业务表 INTEGER 主键体系无关,不与业务表做外键
-- 2) 索引:username / created_at / (module, created_at)
-- 3) 菜单 seed:系统分组下 operation-logs(操作日志 /operation-logs),仅 admin 绑定
-- 幂等:CREATE TABLE IF NOT EXISTS / CREATE INDEX IF NOT EXISTS /
--       菜单与绑定 ON CONFLICT DO NOTHING,父菜单不存在时 SELECT 无结果安全跳过,可重复执行。
-- 执行:docker exec -i inventory-postgres psql -U inv -d inventory < server-java/src/main/resources/db/V16__operation_log.sql
--       (inventory_test 测试库同样执行一遍)

BEGIN;

-- ---------- 1) operation_log:操作日志表 ----------
CREATE TABLE IF NOT EXISTS operation_log (
    id          BIGSERIAL PRIMARY KEY,
    username    VARCHAR(64)  NOT NULL,
    ip          VARCHAR(64),
    module      VARCHAR(64)  NOT NULL,
    action      VARCHAR(32)  NOT NULL,
    path        VARCHAR(255) NOT NULL,
    target_type VARCHAR(64),
    target_id   BIGINT,
    target_no   VARCHAR(64),
    success     SMALLINT     NOT NULL,
    error_msg   VARCHAR(500),
    cost_ms     INTEGER,
    created_at  TIMESTAMP    NOT NULL DEFAULT now()
);

COMMENT ON TABLE operation_log IS '操作日志:全部写操作(POST/PUT/DELETE)执行流水,由切面记录,日志功能故障不影响业务主流程';

CREATE INDEX IF NOT EXISTS idx_operation_log_username   ON operation_log (username);
CREATE INDEX IF NOT EXISTS idx_operation_log_created_at ON operation_log (created_at);
CREATE INDEX IF NOT EXISTS idx_operation_log_module_ct  ON operation_log (module, created_at);

-- ---------- 2) 菜单:系统分组下 操作日志(父菜单不存在时安全跳过,如测试库无菜单树) ----------
INSERT INTO sys_menu (parent_id, menu_code, menu_name, type, path, sort)
SELECT (SELECT id FROM sys_menu WHERE menu_code = 'system-dir'),
       'operation-logs', '操作日志', 'menu', '/operation-logs', 5
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE menu_code = 'system-dir')
ON CONFLICT (menu_code) DO NOTHING;

SELECT setval(pg_get_serial_sequence('sys_menu', 'id'), GREATEST(1, (SELECT MAX(id) FROM sys_menu)));

-- ---------- 3) 角色绑定:仅 admin(审计页面对其他角色不开放) ----------
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.menu_code = 'operation-logs'
WHERE r.role_code = 'admin'
ON CONFLICT (role_id, menu_id) DO NOTHING;

COMMIT;
