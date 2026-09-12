package com.company.inventory.model.vo.monitor;

import java.util.List;

/**
 * 系统监控快照出参(一次取全:CPU/内存/JVM/OS/磁盘)。
 *
 * @param systemCpuUsage   系统级 CPU 占用率(0~1,获取失败为 null)
 * @param processCpuUsage  本进程 CPU 占用率(0~1,获取失败为 null)
 * @param loadAverage      系统负载(getSystemLoadAverage,Windows 返回 -1,前端显示"-")
 * @param mem              物理内存(字节)
 * @param jvm              JVM 信息
 * @param os               操作系统信息
 * @param gcCollectionCount GC 次数(getCollectionCount,不支持为 -1)
 * @param disks            全部磁盘分区
 * @author inventory
 */
public record MonitorVO(Double systemCpuUsage, Double processCpuUsage, Double loadAverage,
        MonitorMemVO mem, MonitorJvmVO jvm, MonitorOsVO os,
        long gcCollectionCount, List<MonitorDiskVO> disks) {
}
