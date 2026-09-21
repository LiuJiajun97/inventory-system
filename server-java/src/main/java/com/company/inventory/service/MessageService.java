package com.company.inventory.service;

import com.company.inventory.common.page.PageResult;
import com.company.inventory.model.vo.log.SysMessageVO;

/**
 * 站内消息服务接口(V25):审批/转换/预警通知。
 *
 * @author inventory
 */
public interface MessageService {

    /**
     * 分页查询当前用户消息(时间倒序)。
     *
     * @param unread 可选,只查未读
     * @param page   页码
     * @param size   页大小
     * @param userId 当前用户 ID
     * @return 分页结果
     */
    PageResult<SysMessageVO> list(Boolean unread, int page, int size, long userId);

    /**
     * 当前用户未读消息数。
     *
     * @param userId 当前用户 ID
     * @return 未读数量
     */
    long unreadCount(long userId);

    /**
     * 标记已读:id 有值单条已读,无 id 全部已读。
     *
     * @param id     可空消息 ID
     * @param userId 当前用户 ID
     * @return 已标记条数
     */
    int markRead(Long id, long userId);

    /**
     * 每日预警汇总:dashboard 挂载调用;查该用户今日是否已有 alert_daily,
     * 有则直接返回;无则汇总当前低库存/临期预警生成一条消息(预警 0 项不生成)。
     *
     * @param userId 当前用户 ID
     * @return 已生成或已存在的那条消息(预警 0 项返回 null)
     */
    SysMessageVO dailyAlert(long userId);

    /**
     * 通用发送入口(审批/转换等场景使用;失败只打 warn,绝不抛出)。
     *
     * @param receiverId 接收人用户 ID
     * @param type       消息类型
     * @param title      标题
     * @param content    内容
     * @param refDocType 关联单据类型(可空)
     * @param refDocId   关联单据 ID(可空)
     */
    void trySendQuietly(long receiverId, String type, String title, String content,
            String refDocType, Long refDocId);

    /**
     * 定时清理保留期前消息(每天 3 点)。
     *
     * @param days 保留天数
     * @return 删除条数
     */
    int cleanup(int days);
}