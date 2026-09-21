package com.company.inventory.common.constant;

import java.util.Map;

/**
 * 导入导出模块的中英文标签映射(与字典表 label 及前端 StatusTag/DocStatusTag 对齐)。
 *
 * <p>导入方向接受中文 label 或英文字典值两种输入;导出方向统一中文输出。
 * 单据状态映射与前端 DocStatusTag.tsx 的 STYLE 完全一致。</p>
 *
 * @author inventory
 */
public final class ImportExportLabels {

    /** 物品分类:字典值 → 中文(与字典表 itemCategory label 一致)。 */
    private static final Map<String, String> CATEGORY_TO_CN = Map.of(
            "raw", "原料",
            "finished", "成品",
            "hardware", "五金");

    /** 物品分类:中文(或字典值) → 字典值(导入用,"原材料"为"原料"的容错别名)。 */
    private static final Map<String, String> CATEGORY_FROM_CN = Map.of(
            "原料", "raw",
            "原材料", "raw",
            "成品", "finished",
            "五金", "hardware",
            "raw", "raw",
            "finished", "finished",
            "hardware", "hardware");

    /** 结算方式:字典值 → 中文(与字典表 settleMethod label 一致)。 */
    private static final Map<String, String> SETTLE_TO_CN = Map.of(
            "month", "月结",
            "prepay", "预付",
            "cod", "货到付款",
            "credit", "账期");

    /** 结算方式:中文(或字典值) → 字典值(导入用)。 */
    private static final Map<String, String> SETTLE_FROM_CN = Map.of(
            "月结", "month",
            "预付", "prepay",
            "货到付款", "cod",
            "账期", "credit",
            "month", "month",
            "prepay", "prepay",
            "cod", "cod",
            "credit", "credit");

    /** 单据状态:英文状态 → 中文(与前端 DocStatusTag 对齐)。 */
    private static final Map<String, String> DOC_STATUS_TO_CN = Map.of(
            "draft", "草稿",
            "pending", "待审批",
            "approved", "已审批",
            "rejected", "已驳回",
            "completed", "已完成",
            "voided", "已作废",
            "closed", "已关闭");

    private ImportExportLabels() {
    }

    /**
     * 物品分类字典值转中文(空或未知值原样返回)。
     *
     * @param value 字典值(可空)
     * @return 中文或原值
     */
    public static String categoryToCn(String value) {
        return value == null ? null : CATEGORY_TO_CN.getOrDefault(value, value);
    }

    /**
     * 物品分类中文(或字典值)转字典值,空串返回 null,未知值抛业务异常。
     *
     * @param input 用户输入(可空)
     * @return 字典值或 null(输入为空时)
     * @throws com.company.inventory.common.exception.BizException 分类无法识别
     */
    public static String categoryFromInput(String input) {
        return mapFromInput(input, CATEGORY_FROM_CN, "分类无法识别");
    }

    /**
     * 结算方式字典值转中文(空或未知值原样返回)。
     *
     * @param value 字典值(可空)
     * @return 中文或原值
     */
    public static String settleToCn(String value) {
        return value == null ? null : SETTLE_TO_CN.getOrDefault(value, value);
    }

    /**
     * 结算方式中文(或字典值)转字典值,空串返回 null,未知值抛业务异常。
     *
     * @param input 用户输入(可空)
     * @return 字典值或 null(输入为空时)
     * @throws com.company.inventory.common.exception.BizException 结算方式无法识别
     */
    public static String settleFromInput(String input) {
        return mapFromInput(input, SETTLE_FROM_CN, "结算方式无法识别");
    }

    /**
     * 单据状态转中文(空或未知值原样返回)。
     *
     * @param status 英文状态(可空)
     * @return 中文或原值
     */
    public static String docStatusToCn(String status) {
        return status == null ? null : DOC_STATUS_TO_CN.getOrDefault(status, status);
    }

    /**
     * 中文/字典值输入映射公共实现(trim 后空返回 null,查不到抛业务异常)。
     *
     * @param input   用户输入(可空)
     * @param map     映射表
     * @param errMsg  无法识别时的错误消息前缀
     * @return 字典值或 null
     * @throws com.company.inventory.common.exception.BizException 输入无法识别
     */
    private static String mapFromInput(String input, Map<String, String> map, String errMsg) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String mapped = map.get(input.trim());
        if (mapped == null) {
            throw new com.company.inventory.common.exception.BizException(
                    errMsg + ": " + input.trim());
        }
        return mapped;
    }
}
