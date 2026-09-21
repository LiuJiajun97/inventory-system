package com.company.inventory.service.impl;

import com.company.inventory.common.constant.MessageType;
import com.company.inventory.common.page.PageResult;
import com.company.inventory.mapper.UserMapper;
import com.company.inventory.mapper.log.SysMessageMapper;
import com.company.inventory.model.entity.log.SysMessageDO;
import com.company.inventory.model.entity.user.UserDO;
import com.company.inventory.model.vo.log.SysMessageVO;
import com.company.inventory.service.AlertService;
import com.company.inventory.service.MessageService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 站内消息服务实现(V25):失败只打 warn 绝不影响主流程。
 *
 * <p>每日预警(dailyAlert):同用户当日只生成 1 条,0 项预警不生成。
 * 清理:每天 3 点按 message.retention-days(默认 90)清理过期消息。</p>
 *
 * @author inventory
 */
@Service
public class MessageServiceImpl implements MessageService {

    /** 日志。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(MessageServiceImpl.class);

    /** 消息 Mapper。 */
    private final SysMessageMapper messageMapper;
    /** 用户 Mapper(用户名 → userId 反查,审批消息通知源单 creator)。 */
    private final UserMapper userMapper;
    /** 预警服务(临期 + 低库存)。 */
    private final AlertService alertService;
    /** 保留天数(message.retention-days,默认 90)。 */
    private final int retentionDays;

    /**
     * 构造服务。
     *
     * @param messageMapper 消息 Mapper
     * @param userMapper    用户 Mapper
     * @param alertService  预警服务
     * @param retentionDays 保留天数
     */
    public MessageServiceImpl(SysMessageMapper messageMapper, UserMapper userMapper,
            AlertService alertService,
            @Value("${message.retention-days:90}") int retentionDays) {
        this.messageMapper = messageMapper;
        this.userMapper = userMapper;
        this.alertService = alertService;
        this.retentionDays = retentionDays;
    }

    /**
     * 分页查询当前用户消息(时间倒序)。
     */
    @Override
    public PageResult<SysMessageVO> list(Boolean unread, int page, int size, long userId) {
        LambdaQueryWrapper<SysMessageDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysMessageDO::getReceiverId, userId);
        if (Boolean.TRUE.equals(unread)) {
            wrapper.eq(SysMessageDO::getRead, false);
        }
        wrapper.orderByDesc(SysMessageDO::getCreatedAt);
        Page<SysMessageDO> pageResult = messageMapper.selectPage(
                Page.of(page, size), wrapper);
        List<SysMessageVO> vos = pageResult.getRecords().stream()
                .map(this::toVO).toList();
        return PageResult.of(vos, pageResult.getTotal(), page, size);
    }

    /**
     * 当前用户未读消息数。
     */
    @Override
    public long unreadCount(long userId) {
        Long count = messageMapper.selectCount(new LambdaQueryWrapper<SysMessageDO>()
                .eq(SysMessageDO::getReceiverId, userId)
                .eq(SysMessageDO::getRead, false));
        return count == null ? 0L : count;
    }

    /**
     * 标记已读:id 有值单条已读,无 id 全部已读。
     */
    @Override
    public int markRead(Long id, long userId) {
        if (id == null) {
            UpdateWrapper<SysMessageDO> wrapper = new UpdateWrapper<>();
            wrapper.set("is_read", true)
                    .eq("receiver_id", userId)
                    .eq("is_read", false);
            return messageMapper.update(null, wrapper);
        }
        UpdateWrapper<SysMessageDO> wrapper = new UpdateWrapper<>();
        wrapper.set("is_read", true)
                    .eq("id", id)
                    .eq("receiver_id", userId);
        return messageMapper.update(null, wrapper);
    }

    /**
     * 每日预警汇总。
     */
    @Override
    public SysMessageVO dailyAlert(long userId) {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        SysMessageDO existing = messageMapper.selectOne(new LambdaQueryWrapper<SysMessageDO>()
                .eq(SysMessageDO::getReceiverId, userId)
                .eq(SysMessageDO::getType, MessageType.ALERT_DAILY)
                .ge(SysMessageDO::getCreatedAt, todayStart)
                .orderByDesc(SysMessageDO::getId)
                .last("LIMIT 1"));
        if (existing != null) {
            return toVO(existing);
        }
        long low = alertService.lowStock(new com.company.inventory.model.query.LowStockQuery() {
            {
                setPage(1);
                setPageSize(1);
            }
        }).total();
        long expiry = alertService.expiry(new com.company.inventory.model.query.ExpiryAlertQuery() {
            {
                setPage(1);
                setPageSize(1);
            }
        }).total();
        long total = low + expiry;
        if (total <= 0) {
            return null;
        }
        String title = "今日预警汇总";
        String content = "今日共 " + total + " 项预警(低库存 " + low + " 项,临期 " + expiry + " 项)";
        SysMessageDO message = new SysMessageDO();
        message.setReceiverId(userId);
        message.setType(MessageType.ALERT_DAILY);
        message.setTitle(title);
        message.setContent(content);
        message.setRead(false);
        message.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(message);
        return toVO(message);
    }

    /**
     * 通用发送入口(失败只打 warn,绝不抛出)。
     */
    @Override
    public void trySendQuietly(long receiverId, String type, String title, String content,
            String refDocType, Long refDocId) {
        try {
            if (receiverId <= 0) {
                return;
            }
            SysMessageDO message = new SysMessageDO();
            message.setReceiverId(receiverId);
            message.setType(type);
            message.setTitle(title);
            message.setContent(content);
            message.setRefDocType(refDocType);
            message.setRefDocId(refDocId);
            message.setRead(false);
            message.setCreatedAt(LocalDateTime.now());
            messageMapper.insert(message);
        } catch (Exception e) {
            LOGGER.warn("发送站内消息失败 type={} receiver={} : {}", type, receiverId, e.getMessage());
        }
    }

    /**
     * 定时清理保留期前消息。
     */
    @Override
    public int cleanup(int days) {
        int deleted = messageMapper.delete(new LambdaQueryWrapper<SysMessageDO>()
                .lt(SysMessageDO::getCreatedAt, LocalDateTime.now().minusDays(days)));
        LOGGER.info("站内消息清理完成:删除 {} 天前消息 {} 条", days, deleted);
        return deleted;
    }

    /**
     * 每天 3 点定时清理。
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void scheduledCleanup() {
        try {
            cleanup(retentionDays);
        } catch (Exception e) {
            LOGGER.warn("站内消息定时清理失败: {}", e.getMessage());
        }
    }

    /**
     * 按用户名查用户 ID(消息通知源单 creator 用)。找不到返回 null。
     *
     * @param username 用户名
     * @return 用户 ID,未找到返回 null
     */
    public Long lookupUserId(String username) {
        if (!org.springframework.util.StringUtils.hasText(username)) {
            return null;
        }
        UserDO user = userMapper.selectOne(new LambdaQueryWrapper<UserDO>()
                .eq(UserDO::getUsername, username));
        return user == null ? null : user.getId();
    }

    /**
     * DO 转 VO。
     *
     * @param d 实体
     * @return VO
     */
    private SysMessageVO toVO(SysMessageDO d) {
        return new SysMessageVO(d.getId(), d.getReceiverId(), d.getType(),
                d.getTitle(), d.getContent(), d.getRefDocType(), d.getRefDocId(),
                d.getRead(), d.getCreatedAt());
    }
}