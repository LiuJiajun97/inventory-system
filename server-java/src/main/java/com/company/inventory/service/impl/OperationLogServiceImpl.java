package com.company.inventory.service.impl;

import com.company.inventory.common.constant.LogModule;
import com.company.inventory.common.support.DateRangeSupport;
import com.company.inventory.common.page.PageResult;
import com.company.inventory.mapper.log.OperationLogMapper;
import com.company.inventory.model.entity.log.OperationLogDO;
import com.company.inventory.model.query.log.OperationLogQuery;
import com.company.inventory.model.vo.log.OperationLogVO;
import com.company.inventory.service.OperationLogService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 操作日志服务实现(V16)。
 *
 * <p>列表:username 模糊 + module 精确 + success 精确 + 日期区间(经
 * DateRangeSupport 解析为 LocalDateTime,禁止字符串直接传 ge/le),时间倒序分页。
 * 清理:每天 3 点定时删除保留天数({@code operation-log.retention-days},默认 180)前的记录。</p>
 *
 * @author inventory
 */
@Service
public class OperationLogServiceImpl implements OperationLogService {

    /** 日志。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(OperationLogServiceImpl.class);

    /** 操作日志 Mapper。 */
    private final OperationLogMapper operationLogMapper;

    /** 保留天数配置(默认 180)。 */
    private final int retentionDays;

    /**
     * 构造服务。
     *
     * @param operationLogMapper 操作日志 Mapper
     * @param retentionDays      保留天数(operation-log.retention-days)
     */
    public OperationLogServiceImpl(OperationLogMapper operationLogMapper,
            @Value("${operation-log.retention-days:180}") int retentionDays) {
        this.operationLogMapper = operationLogMapper;
        this.retentionDays = retentionDays;
    }

    /**
     * 操作日志分页列表(时间倒序)。
     *
     * @param query 查询条件(username/module/success/日期区间/分页)
     * @return 分页结果
     */
    @Override
    public PageResult<OperationLogVO> list(OperationLogQuery query) {
        LambdaQueryWrapper<OperationLogDO> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getUsername())) {
            wrapper.like(OperationLogDO::getUsername, query.getUsername().trim());
        }
        if (StringUtils.hasText(query.getModule())) {
            wrapper.eq(OperationLogDO::getModule, query.getModule().trim());
        }
        if (query.getSuccess() != null) {
            wrapper.eq(OperationLogDO::getSuccess, query.getSuccess());
        }
        LocalDateTime from = DateRangeSupport.parseDateTimeStart(query.getFrom(), "from");
        if (from != null) {
            wrapper.ge(OperationLogDO::getCreatedAt, from);
        }
        LocalDateTime to = DateRangeSupport.parseDateTimeEnd(query.getTo(), "to");
        if (to != null) {
            wrapper.le(OperationLogDO::getCreatedAt, to);
        }
        wrapper.orderByDesc(OperationLogDO::getCreatedAt);
        Page<OperationLogDO> page = operationLogMapper.selectPage(
                Page.of(query.getPage(), query.getPageSize()), wrapper);
        List<OperationLogVO> vos = page.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(vos, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 模块中文名列表(前端筛选下拉,来自 LogModule 集中映射)。
     *
     * @return 模块中文名(有序去重)
     */
    @Override
    public List<String> modules() {
        return List.copyOf(LogModule.names());
    }

    /**
     * 清理保留期前的日志记录。
     *
     * @param days 保留天数(删除早于当前时间 days 天的记录)
     * @return 删除行数
     */
    @Override
    public int cleanup(int days) {
        int deleted = operationLogMapper.delete(new LambdaQueryWrapper<OperationLogDO>()
                .lt(OperationLogDO::getCreatedAt, LocalDateTime.now().minusDays(days)));
        LOGGER.info("操作日志清理完成:删除 {} 天前记录 {} 条", days, deleted);
        return deleted;
    }

    /**
     * 定时任务:每天 3 点清理保留天数前的操作日志(清理失败只告警,不影响业务)。
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void scheduledCleanup() {
        try {
            cleanup(retentionDays);
        } catch (Exception e) {
            LOGGER.warn("操作日志定时清理失败: {}", e.getMessage());
        }
    }

    /**
     * DO 转 VO。
     *
     * @param d 实体
     * @return 视图对象
     */
    private OperationLogVO toVO(OperationLogDO d) {
        return new OperationLogVO(d.getId(), d.getUsername(), d.getIp(), d.getModule(),
                d.getAction(), d.getPath(), d.getTargetType(), d.getTargetId(),
                d.getSuccess(), d.getErrorMsg(), d.getCostMs(), d.getCreatedAt());
    }

}
