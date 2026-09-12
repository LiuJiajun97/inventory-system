package com.company.inventory.monitor;

import com.company.inventory.model.vo.monitor.MonitorDiskVO;
import com.company.inventory.model.vo.monitor.MonitorJvmVO;
import com.company.inventory.model.vo.monitor.MonitorMemVO;
import com.company.inventory.model.vo.monitor.MonitorOsVO;
import com.company.inventory.model.vo.monitor.MonitorVO;
import com.company.inventory.service.impl.MonitorServiceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 系统监控服务测试:只断言"逻辑关系成立",不断言具体数值(运行时环境会变)。
 *
 * @author inventory
 */
class MonitorServiceTest {

    private MonitorServiceImpl service;
    private MonitorVO vo;

    @BeforeEach
    void setUp() {
        service = new MonitorServiceImpl();
        vo = service.overview();
    }

    @Test
    void cpuValuesAreNullOrInRange() {
        // 系统/进程 CPU 占用率要么 null(采样未就绪),要么落在 0~1
        assertTrue(cpuValid(vo.systemCpuUsage()), "系统 CPU 应为 null 或 0~1: " + vo.systemCpuUsage());
        assertTrue(cpuValid(vo.processCpuUsage()), "进程 CPU 应为 null 或 0~1: " + vo.processCpuUsage());
    }

    @Test
    void memUsedEqualsTotalMinusFreeAndFreeNonNegative() {
        MonitorMemVO mem = vo.mem();
        assertNotNull(mem, "物理内存信息不应为空");
        assertTrue(mem.free() >= 0, "空闲内存不应为负: " + mem.free());
        assertTrue(mem.used() == mem.total() - mem.free(), "已用 = 总量 - 空闲");
    }

    @Test
    void heapUsedNotExceedMax() {
        MonitorJvmVO jvm = vo.jvm();
        assertNotNull(jvm, "JVM 信息不应为空");
        assertTrue(jvm.heapUsed() >= 0, "堆已用不应为负: " + jvm.heapUsed());
        assertTrue(jvm.heapFree() >= 0, "堆空闲不应为负: " + jvm.heapFree());
        // 有上限时,已用不超过上限
        if (jvm.heapMax() > 0) {
            assertTrue(jvm.heapUsed() <= jvm.heapMax(), "堆已用不应超过上限");
        }
    }

    @Test
    void disksNonEmptyAndUsableNotExceedTotal() {
        assertNotNull(vo.disks(), "磁盘列表不应为空引用");
        assertTrue(!vo.disks().isEmpty(), "至少应有一个磁盘分区");
        for (MonitorDiskVO disk : vo.disks()) {
            assertTrue(disk.total() >= 0, "磁盘总量不应为负: " + disk.mount());
            assertTrue(disk.free() >= 0, "磁盘空闲不应为负: " + disk.mount());
            assertTrue(disk.usable() <= disk.total(), "可用不应超过总量: " + disk.mount());
        }
    }

    @Test
    void uptimeStartTimeAndMetadataSanity() {
        MonitorJvmVO jvm = vo.jvm();
        MonitorOsVO os = vo.os();
        assertNotNull(os, "OS 信息不应为空");
        assertTrue(jvm.startTime() > 0, "启动时刻应为正");
        assertTrue(jvm.uptimeSeconds() >= 0, "运行时长不应为负: " + jvm.uptimeSeconds());
        assertTrue(jvm.threadCount() > 0, "线程数应为正: " + jvm.threadCount());
        assertTrue(!jvm.javaVersion().isBlank(), "Java 版本不应为空");
        assertTrue(!os.name().isBlank(), "OS 名称不应为空");
    }

    /**
     * CPU 占用率合法性:为 null 或落在 [0,1]。
     *
     * @param value CPU 占用率
     * @return 是否合法
     */
    private boolean cpuValid(Double value) {
        return value == null || (value >= 0 && value <= 1);
    }
}
