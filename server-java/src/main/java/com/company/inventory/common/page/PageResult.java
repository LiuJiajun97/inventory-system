package com.company.inventory.common.page;






import java.util.Collections;
import java.util.List;

/**
 * 分页结果(契约:{rows, total, page, pageSize})。
 *
 * <p>阿里规范:返回集合禁止返回 null,rows 缺省为空集合。</p>
 *
 * @param <T> 行数据类型
 * @author inventory
 */
public record PageResult<T>(List<T> rows, long total, long page, long pageSize) {

    /**
     * 构造分页结果(rows 为 null 时归一为空集合)。
     *
     * @param rows     当前页数据
     * @param total    总条数
     * @param page     页码
     * @param pageSize 每页条数
     * @param <T>      行数据类型
     * @return 分页结果
     */
    public static <T> PageResult<T> of(List<T> rows, long total, long page, long pageSize) {
        List<T> safeRows = rows == null ? Collections.emptyList() : rows;
        return new PageResult<>(safeRows, total, page, pageSize);
    }
}
