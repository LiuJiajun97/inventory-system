package com.company.inventory.dict;

import com.company.inventory.common.exception.BizException;
import com.company.inventory.dto.dict.DictCreateDTO;
import com.company.inventory.dto.dict.DictUpdateDTO;
import com.company.inventory.entity.dict.DictDO;
import com.company.inventory.mapper.DictMapper;
import com.company.inventory.service.DictAdminService;
import com.company.inventory.vo.dict.DictVO;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 字典管理接口测试:新建/编辑/停用/引用校验。
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
class DictAdminTest {

    /** 字典 Mapper。 */
    @Autowired
    private DictMapper dictMapper;
    /** 字典管理服务。 */
    @Autowired
    private DictAdminService dictAdminService;
    /** JDBC。 */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 前置:清空字典表并造测试数据。
     */
    @BeforeAll
    void cleanDb() {
        jdbcTemplate.execute("TRUNCATE \"Dict\" RESTART IDENTITY");
        insert("admTestType", "k1", "标签1", 1, 1);
        insert("admTestType", "k2", "标签2", 2, 1);
        // 插入一条被 Warehouse 引用的字典项(warehouseType)
        insert("warehouseType", "refWh", "引用仓类", 1, 1);
        jdbcTemplate.update(
                "INSERT INTO \"Warehouse\" (\"warehouseCode\",\"warehouseName\","
                        + "\"warehouseType\",\"enableBatch\",\"enableExpiry\","
                        + "\"enableSerial\",\"enableLocation\",\"status\","
                        + "\"createdAt\") VALUES (?, ?, ?, false, false, false, false, 1, NOW())",
                "WH-REF-01", "引用测试仓", "refWh");
    }

    /**
     * 用例 1:admin 新建字典项成功,返回 VO 含 id。
     */
    @Test
    void createDictItem() {
        DictCreateDTO dto = new DictCreateDTO("admTestType", "k3", "标签3", 3, 1);
        DictVO vo = dictAdminService.create(dto);
        assertNotNull(vo.id());
        assertEquals("admTestType", vo.dictType());
        assertEquals("k3", vo.dictKey());
        assertEquals("标签3", vo.dictLabel());
        assertEquals(3, vo.sortOrder());
        assertEquals(1, vo.status());
    }

    /**
     * 用例 2:admin 编辑字典项(dictLabel/sortOrder 可改)。
     */
    @Test
    void updateDictItem() {
        List<DictVO> all = dictAdminService.listAllByType("admTestType");
        DictVO first = all.get(0);
        DictUpdateDTO dto = new DictUpdateDTO("改后标签", 99);
        DictVO updated = dictAdminService.update(first.id(), dto);
        assertEquals("改后标签", updated.dictLabel());
        assertEquals(99, updated.sortOrder());
        // dictType/dictKey 不变
        assertEquals(first.dictType(), updated.dictType());
        assertEquals(first.dictKey(), updated.dictKey());
    }

    /**
     * 用例 3:admin 停用未被引用的字典项成功。
     */
    @Test
    void disableUnreferencedItem() {
        List<DictVO> all = dictAdminService.listAllByType("admTestType");
        DictVO item = all.stream().filter(v -> "k2".equals(v.dictKey())).findFirst().orElseThrow();
        dictAdminService.updateStatus(item.id(), 0);
        DictVO after = dictAdminService.listAllByType("admTestType").stream()
                .filter(v -> v.id().equals(item.id())).findFirst().orElseThrow();
        assertEquals(0, after.status());
    }

    /**
     * 用例 4:admin 停用被引用的字典项被拒(400)。
     */
    @Test
    void disableReferencedItemRejected() {
        List<DictVO> all = dictAdminService.listAllByType("warehouseType");
        DictVO ref = all.stream().filter(v -> "refWh".equals(v.dictKey())).findFirst().orElseThrow();
        BizException ex = assertThrows(BizException.class,
                () -> dictAdminService.updateStatus(ref.id(), 0));
        assertEquals("字典项已被引用,不能停用", ex.getMessage());
    }

    /**
     * 造字典项。
     *
     * @param type  类型
     * @param key   键
     * @param label 标签
     * @param order 排序
     * @param st    状态
     */
    private void insert(String type, String key, String label, int order, int st) {
        DictDO d = new DictDO();
        d.setDictType(type);
        d.setDictKey(key);
        d.setDictLabel(label);
        d.setSortOrder(order);
        d.setStatus(st);
        dictMapper.insert(d);
    }
}
