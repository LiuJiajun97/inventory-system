package com.company.inventory.model.vo.log;

import java.time.LocalDateTime;

/**
 * 站内消息出参(V25):审批/转换/预警通知。
 *
 * @author inventory
 */
public record SysMessageVO(Long id, Long receiverId, String type,
        String title, String content,
        String refDocType, Long refDocId,
        Boolean read, LocalDateTime createdAt) {
}