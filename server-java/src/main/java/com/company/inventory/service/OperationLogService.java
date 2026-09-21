package com.company.inventory.service;

import com.company.inventory.common.page.PageResult;
import com.company.inventory.model.query.log.OperationLogQuery;
import com.company.inventory.model.vo.log.OperationLogVO;

import java.util.List;

/**
 * 操作日志服务接口(V16):写操作流水的查询与保留期清理。
 *
 * @author inventory
 */
public interface OperationLogService {

    /**
     * 操作日志分页列表(时间倒序)。
     *
     * @param query 查询条件(username/module/success/日期区间/分页)
     * @return 分页结果
     */
    PageResult<OperationLogVO> list(OperationLogQuery query);

    /**
     * 模块中文名列表(前端筛选下拉,来自 LogModule 集中映射)。
     *
     * @return 模块中文名(有序去重)
     */
    List<String> modules();

    /**
     * 清理保留期前的日志记录。
     *
     * @param days 保留天数(删除早于当前时间 days 天的记录)
     * @return 删除行数
     */
    int cleanup(int days);

}
