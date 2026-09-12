package com.company.inventory.service;

import com.company.inventory.model.vo.monitor.MonitorVO;

/**
 * 系统监控服务接口(只读本机资源快照,无外部依赖)。
 *
 * @author inventory
 */
public interface MonitorService {

    /**
     * 采集本机资源快照(CPU/内存/JVM/OS/磁盘)。
     *
     * @return 监控快照
     */
    MonitorVO overview();
}
