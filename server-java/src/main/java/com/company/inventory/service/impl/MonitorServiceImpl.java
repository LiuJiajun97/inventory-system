package com.company.inventory.service.impl;

import com.company.inventory.service.MonitorService;
import com.company.inventory.model.vo.monitor.MonitorDiskVO;
import com.company.inventory.model.vo.monitor.MonitorJvmVO;
import com.company.inventory.model.vo.monitor.MonitorMemVO;
import com.company.inventory.model.vo.monitor.MonitorOsVO;
import com.company.inventory.model.vo.monitor.MonitorVO;

import com.sun.management.OperatingSystemMXBean;
import org.springframework.stereotype.Service;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * 系统监控服务实现(只读快照,全部基于 JDK 标准 MBean 与 Runtime,零外部依赖)。
 *
 * @author inventory
 */
@Service
public class MonitorServiceImpl implements MonitorService {

    /**
     * 采集本机资源快照。
     *
     * @return 监控快照
     */
    @Override
    public MonitorVO overview() {
        OperatingSystemMXBean osBean =
                (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        double systemCpu = osBean.getSystemCpuLoad();
        double processCpu = osBean.getProcessCpuLoad();
        double loadAverage = osBean.getSystemLoadAverage();

        long memTotal = osBean.getTotalPhysicalMemorySize();
        long memFree = osBean.getFreePhysicalMemorySize();
        MonitorMemVO mem = new MonitorMemVO(memTotal, memFree, memTotal - memFree);

        MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memBean.getHeapMemoryUsage();
        long gcCount = readGcCollectionCount(memBean);

        RuntimeMXBean rtBean = ManagementFactory.getRuntimeMXBean();
        long startTime = rtBean.getStartTime();
        long uptimeSeconds = (System.currentTimeMillis() - startTime) / 1000L;
        // 堆空闲 = 已提交 - 已用(标准定义,规避个别精简运行时缺失 getFree)
        long heapFree = heap.getCommitted() - heap.getUsed();
        MonitorJvmVO jvm = new MonitorJvmVO(
                heap.getMax(), heap.getUsed(), heapFree,
                ManagementFactory.getThreadMXBean().getThreadCount(), startTime, uptimeSeconds,
                System.getProperty("java.version"));

        MonitorOsVO os = new MonitorOsVO(
                System.getProperty("os.name"),
                System.getProperty("os.arch"),
                System.getProperty("user.name"));

        return new MonitorVO(
                toUsageOrNull(systemCpu),
                toUsageOrNull(processCpu),
                loadAverage,
                mem,
                jvm,
                os,
                gcCount,
                collectDisks());
    }

    /**
     * 遍历所有根盘符收集容量,不可用盘(getTotalSpace 返回 -1)跳过。
     *
     * @return 全部可用磁盘分区列表
     */
    private List<MonitorDiskVO> collectDisks() {
        List<MonitorDiskVO> disks = new ArrayList<>();
        for (File root : File.listRoots()) {
            long total = root.getTotalSpace();
            // 空分区(未格式化的盘符)total 为 0,与 -1(不可用)一并跳过
            if (total <= 0) {
                continue;
            }
            disks.add(new MonitorDiskVO(
                    root.getPath(), total, root.getFreeSpace(), root.getUsableSpace()));
        }
        return disks;
    }

    /**
     * CPU 占用率转可空值:负值(采样未就绪)转 null,否则原值(0~1)。
     *
     * @param load 原始 CPU 占用率(可能为 -1)
     * @return 0~1 的占用率,或 null 表示获取失败
     */
    private Double toUsageOrNull(double load) {
        return load < 0 ? null : load;
    }

    /**
     * 读取 GC 次数:个别精简运行时缺失 getCollectionCount,反射调用并按约定降级为 -1(不支持)。
     *
     * @param memBean 内存 MBean
     * @return GC 次数,或 -1 表示当前运行时不支持
     */
    private long readGcCollectionCount(MemoryMXBean memBean) {
        try {
            Method method = memBean.getClass().getMethod("getCollectionCount");
            Object value = method.invoke(memBean);
            if (value instanceof Number number) {
                return number.longValue();
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            // 当前运行时不支持 GC 计数,按契约返回 -1
        }
        return -1L;
    }
}
