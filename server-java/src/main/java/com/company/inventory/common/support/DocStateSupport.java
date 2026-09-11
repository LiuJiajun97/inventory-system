package com.company.inventory.common.support;

import com.company.inventory.common.constant.DocStatus;
import com.company.inventory.common.exception.BizException;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 单据状态机通用支撑:条件 UPDATE 原子状态迁移(防并发双击/重复操作)。
 *
 * <p>所有单据表头统一含 status/updater/updatedAt 列(camelCase 加引号),
 * 迁移影响行数为 0 即状态冲突,抛业务异常回滚。</p>
 *
 * @author inventory
 */
@Component
public class DocStateSupport {

    /**
     * 条件迁移状态:仅当前状态属于 from 集合才允许迁移到 to。
     *
     * @param mapper   单据表头 Mapper
     * @param id       单据 ID
     * @param from     允许的来源状态集合(非空)
     * @param to       目标状态
     * @param updater  操作人(username)
     * @param <T>      表头实体类型
     * @return 影响行数(1 成功 / 0 状态冲突)
     */
    public <T> int transition(BaseMapper<T> mapper, long id, List<String> from, String to, String updater) {
        UpdateWrapper<T> wrapper = new UpdateWrapper<>();
        wrapper.set("\"status\"", to)
                .set("\"updater\"", updater)
                .set("\"updatedAt\"", LocalDateTime.now())
                .eq("\"id\"", id);
        if (from.size() == 1) {
            wrapper.eq("\"status\"", from.get(0));
        } else {
            wrapper.in("\"status\"", from);
        }
        return mapper.update(null, wrapper);
    }

    /**
     * 条件迁移状态并附加额外字段(如审批人/审批时间),原子操作。
     *
     * @param mapper   单据表头 Mapper
     * @param id       单据 ID
     * @param from     允许的来源状态集合(非空)
     * @param to       目标状态
     * @param updater  操作人(username)
     * @param extra    额外设置列(列名为已加引号 camelCase)→ 值
     * @param <T>      表头实体类型
     * @return 影响行数(1 成功 / 0 状态冲突)
     */
    public <T> int transitionWith(BaseMapper<T> mapper, long id, List<String> from, String to,
            String updater, java.util.Map<String, Object> extra) {
        UpdateWrapper<T> wrapper = new UpdateWrapper<>();
        wrapper.set("\"status\"", to)
                .set("\"updater\"", updater)
                .set("\"updatedAt\"", LocalDateTime.now());
        if (extra != null) {
            for (java.util.Map.Entry<String, Object> e : extra.entrySet()) {
                wrapper.set(e.getKey(), e.getValue());
            }
        }
        wrapper.eq("\"id\"", id);
        if (from.size() == 1) {
            wrapper.eq("\"status\"", from.get(0));
        } else {
            wrapper.in("\"status\"", from);
        }
        return mapper.update(null, wrapper);
    }

    /**
     * 校验终态:终态单据整单只读,任何写操作直接拒绝。
     *
     * @param status  当前状态
     * @param docName 单据中文名(用于错误提示)
     */
    public void assertWritable(String status, String docName) {
        if (DocStatus.isTerminal(status)) {
            throw new BizException(docName + "已处于终态(" + status + "),不可再操作");
        }
    }
}
