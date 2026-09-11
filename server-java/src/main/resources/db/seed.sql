-- 种子数据:3 仓库 + 4 物品 + 4 库位 + 3 用户
-- 密码 BCrypt(明文:admin123 / zhang123 / lisi123)
-- 用法(新环境):psql -U inv -d inventory -f schema.sql -f seed.sql

BEGIN;

INSERT INTO warehouse
    (id, warehouse_code, warehouse_name, warehouse_type, enable_batch, enable_expiry, enable_serial, enable_location, status)
VALUES
    (1, 'RAW01', '原材料仓', 'raw', TRUE, TRUE, FALSE, FALSE, 1),
    (2, 'FIN01', '成品仓', 'finished', TRUE, TRUE, TRUE, TRUE, 1),
    (3, 'HWD01', '五金仓', 'hardware', FALSE, FALSE, FALSE, TRUE, 1);
SELECT setval(pg_get_serial_sequence('warehouse', 'id'), (SELECT MAX(id) FROM warehouse));

INSERT INTO location (warehouse_id, location_code, location_name)
VALUES
    (2, 'A-01', 'A 区-01'),
    (2, 'A-02', 'A 区-02'),
    (3, 'H-01', 'H 区-01'),
    (3, 'H-02', 'H 区-02');
SELECT setval(pg_get_serial_sequence('location', 'id'), (SELECT MAX(id) FROM location));

INSERT INTO item (item_code, item_name, unit, spec, attributes)
VALUES
    ('HW-SCREW-M8', '螺丝 M8', '支', 'M8x20', '{"material":"碳钢","brand":"某品牌"}'),
    ('HW-BEAR-6204', '轴承 6204', '套', '6204-2RS', '{"model":"6204"}'),
    ('RAW-RESIN', '原料树脂', '公斤', NULL, '{"purity":"99%"}'),
    ('FIN-CASE', '成品机箱', '台', '标准型', '{"color":"黑色"}');
SELECT setval(pg_get_serial_sequence('item', 'id'), (SELECT MAX(id) FROM item));

INSERT INTO sys_user (username, password_hash, name, role, status)
VALUES
    ('admin', '$2a$10$5n/UIQPxHrOHF58VqYR4D.BJDARMGzaMfXkCbeBW3r6lwDFy80cue', '系统管理员', 'admin', 1),
    ('zhangsan', '$2a$10$Evw2jjo47lTokuMjmtWn7eMmgkAUCVCvn6Pj/PZ/bXqrzgEODLjYy', '张三', 'operator', 1),
    ('lisi', '$2a$10$Fn68gMOOb00gtyeA5QrkC.DZcwKR/JTmLL3o9zosXOA3ABTZTm8PO', '李四', 'viewer', 1);
SELECT setval(pg_get_serial_sequence('sys_user', 'id'), (SELECT MAX(id) FROM sys_user));

COMMIT;
