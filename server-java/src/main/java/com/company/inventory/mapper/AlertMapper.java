package com.company.inventory.mapper;

import com.company.inventory.query.ExpiryAlertQuery;
import com.company.inventory.query.LowStockQuery;
import com.company.inventory.vo.alert.ExpiryAlertRow;
import com.company.inventory.vo.alert.LowStockRow;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 预警查询 Mapper(自定义 SQL,分页由 MyBatis-Plus 分页插件在数据库层完成)。
 *
 * <p>注意:本接口无对应实体,不继承泛型约束,仅承载自定义分页查询;
 * 空集合防护由 SQL 层 &lt;if&gt; 判断保证,禁止空 IN。</p>
 *
 * @author inventory
 */
@Mapper
public interface AlertMapper {

    /**
     * 临期预警分页查询(到期日 ≤ today+N 天且有库存的批次行)。
     *
     * @param page        分页对象
     * @param query       查询条件
     * @param alertDays   临期天数阈值
     * @return 分页结果
     */
    IPage<ExpiryAlertRow> selectExpiryAlerts(@Param("page") Page<ExpiryAlertRow> page,
            @Param("query") ExpiryAlertQuery query, @Param("alertDays") int alertDays);

    /**
     * 低库存预警分页查询(minStock 非空且全仓可用量 < minStock 的物品)。
     *
     * @param page  分页对象
     * @param query 查询条件
     * @return 分页结果
     */
    IPage<LowStockRow> selectLowStock(@Param("page") Page<LowStockRow> page,
            @Param("query") LowStockQuery query);
}
