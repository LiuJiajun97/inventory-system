package com.company.inventory.model.vo.monitor;

/**
 * 物理内存出参(单位:字节)。
 *
 * @param total 物理内存总量(字节)
 * @param free  空闲内存(字节)
 * @param used  已用内存(字节,total - free)
 * @author inventory
 */
public record MonitorMemVO(long total, long free, long used) {
}
