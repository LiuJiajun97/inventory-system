package com.company.inventory.controller;

import com.company.inventory.common.page.PageResult;
import com.company.inventory.config.RequireRole;
import com.company.inventory.model.query.log.OperationLogQuery;
import com.company.inventory.model.vo.log.OperationLogVO;
import com.company.inventory.service.OperationLogService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 操作日志接口(仅 admin,只读审计:写操作流水查询 + 模块筛选项)。
 *
 * @author inventory
 */
@Tag(name = "操作日志")
@RestController
@RequestMapping("/api/v1/operation-logs")
@RequireRole("admin")
public class OperationLogController {

    /** 操作日志服务。 */
    private final OperationLogService operationLogService;

    /**
     * 构造控制器。
     *
     * @param operationLogService 操作日志服务
     */
    public OperationLogController(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    /**
     * 操作日志分页列表(时间倒序,username/module/success/日期区间筛选)。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Operation(summary = "操作日志列表")
    @GetMapping
    public PageResult<OperationLogVO> list(@Valid OperationLogQuery query) {
        return operationLogService.list(query);
    }

    /**
     * 模块中文名列表(前端筛选下拉)。
     *
     * @return 模块中文名(有序去重)
     */
    @Operation(summary = "模块筛选项")
    @GetMapping("/modules")
    public List<String> modules() {
        return operationLogService.modules();
    }

}
