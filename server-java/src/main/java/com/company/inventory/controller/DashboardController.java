package com.company.inventory.controller;

import com.company.inventory.service.DashboardService;
import com.company.inventory.model.vo.dashboard.DashboardVO;







import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 总览接口。
 *
 * @author inventory
 */
@Tag(name = "总览")
@RestController
@RequestMapping("/api/v1/dashboard")
@Validated
public class DashboardController {

    /** 总览服务。 */
    private final DashboardService dashboardService;

    /**
     * 构造控制器。
     *
     * @param dashboardService 总览服务
     */
    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /**
     * 总览统计。
     *
     * @return 总览数据
     */
    @Operation(summary = "总览统计")
    @GetMapping("/summary")
    public DashboardVO summary() {
        return dashboardService.summary();
    }
}
