-- V21: 列表页表格偏好(列宽拖拽/列显隐/列序)按用户持久化
-- 背景:前端 ProTable 支持拖拽列宽、列显隐勾选、列拖拽排序,配置按 (用户, 页面) 存库,
-- 后端加一层 Redis 缓存(前缀 pref:table:{userId},TTL 300 秒,fail-open 降级直查库)。
-- config 为 JSONB,存前端序列化的 {widths, hidden, order} 原文,服务端不做结构强校验。
-- 幂等:IF NOT EXISTS / ON CONFLICT,可重复执行。
-- 执行:docker exec -i inventory-postgres psql -U inv -d inventory < server-java/src/main/resources/db/V21__table_pref.sql
--       (inventory_test 测试库同样执行一遍)

CREATE TABLE IF NOT EXISTS sys_user_table_pref (
    id          bigserial NOT NULL,
    user_id     bigint NOT NULL,
    page_key    varchar(64) NOT NULL,
    config      jsonb NOT NULL,
    creator     varchar(64),
    created_at  timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater     varchar(64),
    updated_at  timestamp without time zone,
    CONSTRAINT uk_user_table_pref_pkey PRIMARY KEY (id),
    CONSTRAINT uk_user_page UNIQUE (user_id, page_key)
);

COMMENT ON TABLE public.sys_user_table_pref IS '用户表格偏好:按 (用户, 页面) 存 ProTable 列宽/显隐/列序配置(config JSONB 存前端原文)。';

COMMENT ON COLUMN public.sys_user_table_pref.id IS '主键ID';
COMMENT ON COLUMN public.sys_user_table_pref.user_id IS '用户 ID';
COMMENT ON COLUMN public.sys_user_table_pref.page_key IS '页面标识(前端路由段,如 purchase-list)';
COMMENT ON COLUMN public.sys_user_table_pref.config IS '列配置 JSON 原文:{widths 列宽, hidden 隐藏列, order 列序}';
COMMENT ON COLUMN public.sys_user_table_pref.creator IS '创建人';
COMMENT ON COLUMN public.sys_user_table_pref.created_at IS '创建时间';
COMMENT ON COLUMN public.sys_user_table_pref.updater IS '更新人';
COMMENT ON COLUMN public.sys_user_table_pref.updated_at IS '更新时间';

CREATE INDEX IF NOT EXISTS idx_user_table_pref_user_id ON public.sys_user_table_pref USING btree (user_id);
