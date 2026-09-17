-- [已归档 2026-09-17] 历史增量迁移记录,仅作变更记录保留;新环境初始化只需执行 schema.sql + seed.sql(终态),勿再执行本文件。
-- V19 盘点差异生成调整单防重复(幂等修复)
-- 背景:generateAdjust 无防重校验,同一盘点单可无限次生成盘盈/盘亏调整单(ref_doc_no 仅为文本记录),
-- 调整单每执行一次动一次库存,重复生成导致盘盈/盘亏被重复计入,账实不符。
-- 修复:同一盘点单(ref_doc_no)同一调整类型(adjust_type)最多一张非作废调整单;
-- 全部作废后允许重新生成(部分索引排除 voided);手工调整单 ref_doc_no 为 null,不受约束。
-- 应用层前置校验为主(StocktakeServiceImpl.generateAdjust),本索引为并发竞态兜底。
-- 注意:生产库 V19 灌库前须先处理存量重复行(TZ-20260913-0001,数据修复单),否则建索引报 duplicate key。

CREATE UNIQUE INDEX IF NOT EXISTS uk_adjust_ref_doc_type
    ON stock_adjust_doc (ref_doc_no, adjust_type)
    WHERE ref_doc_no IS NOT NULL AND status <> 'voided';
