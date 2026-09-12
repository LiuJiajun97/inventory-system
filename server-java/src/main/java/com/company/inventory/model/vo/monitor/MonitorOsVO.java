package com.company.inventory.model.vo.monitor;

/**
 * 操作系统信息出参。
 *
 * @param name     操作系统名称(System.getProperty("os.name"))
 * @param arch     系统架构(System.getProperty("os.arch"))
 * @param userName 当前运行用户(System.getProperty("user.name"))
 * @author inventory
 */
public record MonitorOsVO(String name, String arch, String userName) {
}
