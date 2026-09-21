package com.company.inventory.message;

import com.company.inventory.common.constant.MessageType;
import com.company.inventory.common.page.PageResult;
import com.company.inventory.mapper.log.SysMessageMapper;
import com.company.inventory.model.entity.log.SysMessageDO;
import com.company.inventory.model.vo.log.SysMessageVO;
import com.company.inventory.service.MessageService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 站内消息服务测试(V25):发送/标记已读/每日预警。
 *
 * <p>消息发送失败绝不抛出(由调用方 try-catch),此处只验证常规行为。</p>
 *
 * @author inventory
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5433/inventory_test",
        "spring.datasource.username=inv",
        "spring.datasource.password=inv123"
})
class MessageTest {

    @Autowired
    private MessageService messageService;
    @Autowired
    private SysMessageMapper messageMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanMessages() {
        jdbcTemplate.update("DELETE FROM sys_message");
    }

    /**
     * 用例 1:发送消息 → 落库 + read=false。
     */
    @Test
    void trySendStoresMessage() {
        messageService.trySendQuietly(1001L, MessageType.APPROVAL, "标题",
                "内容", "sales_order", 99L);
        SysMessageDO row = messageMapper.selectOne(null);
        assertNotNull(row);
        assertEquals(1001L, row.getReceiverId());
        assertEquals(MessageType.APPROVAL, row.getType());
        assertEquals("标题", row.getTitle());
        assertEquals("内容", row.getContent());
        assertEquals("sales_order", row.getRefDocType());
        assertEquals(99L, row.getRefDocId());
        assertEquals(Boolean.FALSE, row.getRead());
    }

    /**
     * 用例 2:未读消息数 + 分页查询。
     */
    @Test
    void listAndUnreadCount() {
        messageService.trySendQuietly(1001L, MessageType.APPROVAL, "t1", "c1", null, null);
        messageService.trySendQuietly(1001L, MessageType.CONVERSION, "t2", "c2", null, null);
        messageService.trySendQuietly(2002L, MessageType.APPROVAL, "t3", "c3", null, null);
        assertEquals(2L, messageService.unreadCount(1001L));
        PageResult<SysMessageVO> page = messageService.list(null, 1, 20, 1001L);
        assertEquals(2L, page.total());
    }

    /**
     * 用例 3:标记单条已读 → unreadCount 减 1。
     */
    @Test
    void markReadById() {
        messageService.trySendQuietly(1001L, MessageType.APPROVAL, "t1", "c1", null, null);
        List<SysMessageDO> rows = messageMapper.selectList(null);
        SysMessageDO first = rows.get(0);
        int n = messageService.markRead(first.getId(), 1001L);
        assertEquals(1, n);
        assertEquals(0L, messageService.unreadCount(1001L));
    }

    /**
     * 用例 4:无 id → 全部已读。
     */
    @Test
    void markReadAll() {
        messageService.trySendQuietly(1001L, MessageType.APPROVAL, "t1", "c1", null, null);
        messageService.trySendQuietly(1001L, MessageType.CONVERSION, "t2", "c2", null, null);
        int n = messageService.markRead(null, 1001L);
        assertEquals(2, n);
        assertEquals(0L, messageService.unreadCount(1001L));
    }

    /**
     * 用例 5:每日预警 — 当预警 0 项时不生成消息(返回 null);已生成过当日不重复生成。
     */
    @Test
    void dailyAlert() {
        SysMessageVO vo = messageService.dailyAlert(1001L);
        // 库存预警依赖基础数据,本测试库无 stock 表大量数据 → 大概率 0 项返回 null
        if (vo == null) {
            assertNull(messageService.dailyAlert(1001L));
        } else {
            SysMessageVO second = messageService.dailyAlert(1001L);
            assertNotNull(second);
            // 同日重复调用应返回同一条
            assertTrue(second.id().equals(vo.id()) || second.createdAt().equals(vo.createdAt()));
        }
    }

    /**
     * 用例 6:unread=true 过滤。
     */
    @Test
    void listUnreadOnly() {
        messageService.trySendQuietly(1001L, MessageType.APPROVAL, "t1", "c1", null, null);
        messageService.trySendQuietly(1001L, MessageType.CONVERSION, "t2", "c2", null, null);
        List<SysMessageDO> rows = messageMapper.selectList(null);
        SysMessageDO first = rows.get(0);
        messageService.markRead(first.getId(), 1001L);
        PageResult<SysMessageVO> page = messageService.list(true, 1, 20, 1001L);
        assertEquals(1L, page.total());
    }
}