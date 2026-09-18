package com.company.inventory.controller;

import com.company.inventory.config.RequirePerm;
import com.company.inventory.service.MonitorService;
import com.company.inventory.model.vo.monitor.MonitorVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统监控接口(仅 admin,只读本机资源快照)。
 *
 * @author inventory
 */
@Tag(name = "系统监控")
@RestController
@RequestMapping("/api/v1/monitor")
@RequirePerm("monitor:view")
@Validated
public class MonitorController {

    /** 监控服务。 */
    private final MonitorService monitorService;

    /**
     * 构造控制器。
     *
     * @param monitorService 监控服务
     */
    public MonitorController(MonitorService monitorService) {
        this.monitorService = monitorService;
    }

    /**
     * 本机资源快照(CPU/内存/JVM/OS/磁盘,一次取全)。
     *
     * @return 监控快照
     */
    @Operation(summary = "本机资源快照")
    @GetMapping("/overview")
    public MonitorVO overview() {
        return monitorService.overview();
    }
}
