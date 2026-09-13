package com.company.inventory.model.vo.excel;

import java.util.List;

/**
 * 导入结果出参(HTTP 200,部分失败也 200)。
 *
 * @param imported 成功导入行数
 * @param failed   失败行汇总(行号为 Excel 行号,含表头则数据行从 2 起)
 * @author inventory
 */
public record ImportResultVO(long imported, List<FailedRow> failed) {

    /**
     * 失败行。
     *
     * @param row    Excel 行号(1 起,第 1 行为表头)
     * @param code   该行编码(必填列缺失时为空串)
     * @param reason 失败原因(中文)
     * @author inventory
     */
    public record FailedRow(int row, String code, String reason) {
    }
}
