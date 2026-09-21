package com.company.inventory.model.vo.monitor;

/**
 * JVM 信息出参(堆内存单位:字节,时间为毫秒/秒)。
 *
 * @param heapMax        堆内存上限(字节)
 * @param heapUsed       堆已用(字节)
 * @param heapFree       堆空闲(字节)
 * @param threadCount    当前活动线程数
 * @param startTime      JVM 启动时刻(毫秒时间戳)
 * @param uptimeSeconds  已运行时长(秒)
 * @param javaVersion    Java 版本(System.getProperty("java.version"))
 * @author inventory
 */
public record MonitorJvmVO(long heapMax, long heapUsed, long heapFree,
        long threadCount, long startTime, long uptimeSeconds, String javaVersion) {
}
