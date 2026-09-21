package com.company.inventory.dict;

import com.company.inventory.model.entity.dict.DictDO;
import com.company.inventory.mapper.DictMapper;
import com.company.inventory.service.DictService;
import com.company.inventory.model.vo.dict.DictOptionVO;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 字典服务测试:按类型过滤 / sortOrder 排序 / status=0 不返回。
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
class DictTest {

    /** 字典 Mapper */
    @Autowired
    private DictMapper dictMapper;
    /** 字典服务 */
    @Autowired
    private DictService dictService;
    /** JDBC */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 前置:清空字典表并造测试数据(含乱序 sortOrder 与停用项)。
     */
    @BeforeAll
    void cleanDb() {
        jdbcTemplate.execute("TRUNCATE \"dict\" RESTART IDENTITY");
        insert("whType", "z", "Z 项", 3);
        insert("whType", "a", "A 项", 1);
        insert("whType", "m", "M 项", 2);
        insert("otherType", "x", "他类项", 0);
        DictDO disabled = new DictDO();
        disabled.setDictType("whType");
        disabled.setDictKey("off");
        disabled.setDictLabel("停用项");
        disabled.setSortOrder(0);
        disabled.setStatus(0);
        dictMapper.insert(disabled);
    }

    /**
     * 用例 1:按 type 过滤,只返回该类型。
     */
    @Test
    void filterByType() {
        List<DictOptionVO> list = dictService.listByType("whType");
        assertEquals(3, list.size());
        // 不应混入其他类型
        list.forEach(v -> assertEquals(false, "x".equals(v.code())));
        List<DictOptionVO> other = dictService.listByType("otherType");
        assertEquals(1, other.size());
        assertEquals("他类项", other.get(0).label());
    }

    /**
     * 用例 2:按 sortOrder 升序。
     */
    @Test
    void orderBySortOrder() {
        List<DictOptionVO> list = dictService.listByType("whType");
        assertEquals(List.of("a", "m", "z"), list.stream().map(DictOptionVO::code).toList());
    }

    /**
     * 用例 3:status=0 不返回。
     */
    @Test
    void disabledNotReturned() {
        List<DictOptionVO> list = dictService.listByType("whType");
        list.forEach(v -> assertEquals(false, "off".equals(v.code())));
    }

    /**
     * 造字典项。
     *
     * @param type  类型
     * @param key   键
     * @param label 标签
     * @param order 排序
     */
    private void insert(String type, String key, String label, int order) {
        DictDO d = new DictDO();
        d.setDictType(type);
        d.setDictKey(key);
        d.setDictLabel(label);
        d.setSortOrder(order);
        d.setStatus(1);
        dictMapper.insert(d);
    }
}
