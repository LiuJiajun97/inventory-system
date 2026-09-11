package com.company.inventory.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.inventory.entity.dict.DictDO;
import com.company.inventory.mapper.DictMapper;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 字典引用校验注册表:集中管理字典类型与业务表的引用关系。
 *
 * <p>停用字典项/类型前通过此注册表查询是否被业务表引用,避免硬编码 switch。</p>
 *
 * @author inventory
 */
@Component
public class DictReferenceRegistry {

    /** 引用表记录:(表名, 列名, 显示名)。 */
    public record TableRef(String table, String column, String displayName) {
    }

    /** 注册表:字典类型 → 引用列表。 */
    private final Map<String, List<TableRef>> registry;

    /** JDBC 模板。 */
    private final JdbcTemplate jdbcTemplate;

    /** 字典 Mapper。 */
    private final DictMapper dictMapper;

    /**
     * 构造注册表(硬编码现有 3 类引用关系)。
     *
     * @param jdbcTemplate JDBC 模板
     * @param dictMapper   字典 Mapper
     */
    public DictReferenceRegistry(JdbcTemplate jdbcTemplate, DictMapper dictMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.dictMapper = dictMapper;
        this.registry = Map.of(
                "warehouseType", List.of(
                        new TableRef("Warehouse", "warehouseType", "仓库")),
                "itemCategory", List.of(
                        new TableRef("Item", "category", "物品")),
                "settleMethod", List.of(
                        new TableRef("Supplier", "settleMethod", "供应商"),
                        new TableRef("Customer", "settleMethod", "客户"))
        );
    }

    /**
     * 校验字典项是否被业务表引用。
     *
     * @param dictType 字典类型
     * @param dictKey  字典键值
     * @return 是否被引用
     */
    public boolean isDictReferenced(String dictType, String dictKey) {
        List<TableRef> refs = registry.get(dictType);
        if (refs == null || refs.isEmpty()) {
            return false;
        }
        try {
            for (TableRef ref : refs) {
                Long count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM \"" + ref.table()
                                + "\" WHERE \"" + ref.column() + "\" = ?",
                        Long.class, dictKey);
                if (count != null && count > 0) {
                    return true;
                }
            }
        } catch (org.springframework.jdbc.BadSqlGrammarException e) {
            return false;
        }
        return false;
    }

    /**
     * 获取字典项被引用的显示名(用于错误提示)。
     *
     * @param dictType 字典类型
     * @param dictKey  字典键值
     * @return 引用显示名,无引用返回 null
     */
    public String getRefDisplayName(String dictType, String dictKey) {
        List<TableRef> refs = registry.get(dictType);
        if (refs == null || refs.isEmpty()) {
            return null;
        }
        try {
            for (TableRef ref : refs) {
                Long count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM \"" + ref.table()
                                + "\" WHERE \"" + ref.column() + "\" = ?",
                        Long.class, dictKey);
                if (count != null && count > 0) {
                    return ref.displayName();
                }
            }
        } catch (org.springframework.jdbc.BadSqlGrammarException e) {
            return null;
        }
        return null;
    }

    /**
     * 校验字典类型是否被业务表引用(停用类型前调用)。
     *
     * <p>查询该类型下所有字典项,逐项检查是否被注册表中的业务表引用。</p>
     *
     * @param typeCode 类型编码
     * @return 是否被引用
     */
    public boolean isTypeReferenced(String typeCode) {
        List<TableRef> refs = registry.get(typeCode);
        if (refs == null || refs.isEmpty()) {
            return false;
        }
        List<DictDO> items = dictMapper.selectList(
                new LambdaQueryWrapper<DictDO>()
                        .eq(DictDO::getDictType, typeCode));
        try {
            for (DictDO item : items) {
                for (TableRef ref : refs) {
                    Long count = jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM \"" + ref.table()
                                    + "\" WHERE \"" + ref.column() + "\" = ?",
                            Long.class, item.getDictKey());
                    if (count != null && count > 0) {
                        return true;
                    }
                }
            }
        } catch (org.springframework.jdbc.BadSqlGrammarException e) {
            return false;
        }
        return false;
    }
}
