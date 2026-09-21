package com.company.inventory.model.vo.monitor;

/**
 * 磁盘分区出参(单位:字节,全部盘符各一行)。
 *
 * @param mount  盘符挂载点(如 Windows 的 "C:\")
 * @param total  总容量(字节)
 * @param free   空闲空间(字节)
 * @param usable 可用空间(字节,受用户配额/预留限制)
 * @author inventory
 */
public record MonitorDiskVO(String mount, long total, long free, long usable) {
}
