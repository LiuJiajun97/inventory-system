package com.company.inventory.dict;

import com.company.inventory.common.exception.BizException;
import com.company.inventory.model.dto.dict.DictTypeCreateDTO;
import com.company.inventory.model.dto.dict.DictTypeUpdateDTO;
import com.company.inventory.model.entity.dict.DictDO;
import com.company.inventory.model.entity.dict.DictTypeDO;
import com.company.inventory.mapper.DictMapper;
import com.company.inventory.mapper.DictTypeMapper;
import com.company.inventory.service.DictTypeAdminService;
import com.company.inventory.model.vo.dict.DictTypeVO;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 字典类型管理接口测试:新建/编辑/停用/引用校验/角色隔离。
 *
 * @author inventory
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5433/inventory_test",
        "spring.datasource.username=inv",
        "spring.datasource.password=inv123"
})
class DictTypeTest {

    /** 字典类型 Mapper。 */
    @Autowired
    private DictTypeMapper dictTypeMapper;
    /** 字典 Mapper。 */
    @Autowired
    private DictMapper dictMapper;
    /** 字典类型管理服务。 */
    @Autowired
    private DictTypeAdminService dictTypeAdminService;
    /** JDBC。 */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 前置:清空字典类型表和字典表并造测试数据。
     */
    @BeforeAll
    void cleanDb() {
        jdbcTemplate.execute("TRUNCATE \"dict\" RESTART IDENTITY");
        jdbcTemplate.execute("TRUNCATE \"dict_type\" RESTART IDENTITY");
        insertType("testtypea", "测试类型A", "备注A", 1);
        insertType("testtypeb", "测试类型B", "备注B", 1);
        insertType("warehouseType", "仓库类型", "测试用", 1);
        insertDictItem("testtypea", "k1", "标签1", 1, 1);
        insertDictItem("testtypea", "k2", "标签2", 2, 1);
    }

    /**
     * 用例 1:admin 新建字典类型成功。
     */
    @Test
    @Order(1)
    void createDictType() {
        DictTypeCreateDTO dto = new DictTypeCreateDTO("newtesttype", "新建测试类型", "新建备注");
        DictTypeVO vo = dictTypeAdminService.create(dto);
        assertNotNull(vo.id());
        assertEquals("newtesttype", vo.typeCode());
        assertEquals("新建测试类型", vo.typeName());
        assertEquals("新建备注", vo.remark());
        assertEquals(1, vo.status());
        assertEquals(0L, vo.enabledCount());
    }

    /**
     * 用例 2:typeCode 格式非法时拒绝(400)。
     */
    @Test
    @Order(2)
    void rejectInvalidTypeCode() {
        DictTypeCreateDTO dto = new DictTypeCreateDTO("123invalid", "非法编码", "");
        BizException ex = assertThrows(BizException.class,
                () -> dictTypeAdminService.create(dto));
        assertTrue(ex.getMessage().contains("类型编码"),
                "期望包含'类型编码',实际: " + ex.getMessage());
    }

    /**
     * 用例 3:typeCode 重复时拒绝(400)。
     */
    @Test
    @Order(3)
    void rejectDuplicateTypeCode() {
        DictTypeCreateDTO dto = new DictTypeCreateDTO("testtypea", "重复类型", "");
        BizException ex = assertThrows(BizException.class,
                () -> dictTypeAdminService.create(dto));
        assertTrue(ex.getMessage().contains("已存在"),
                "期望包含'已存在',实际: " + ex.getMessage());
    }

    /**
     * 用例 4:类型下有启用项时停用被拒(400)。
     */
    @Test
    @Order(4)
    void disableTypeWithEnabledItemsRejected() {
        DictTypeUpdateDTO dto = new DictTypeUpdateDTO("测试类型A", "备注A", 0);
        BizException ex = assertThrows(BizException.class,
                () -> dictTypeAdminService.update("testtypea", dto));
        assertTrue(ex.getMessage().contains("启用项"),
                "期望包含'启用项',实际: " + ex.getMessage());
    }

    /**
     * 用例 5:被业务表引用的类型停用被拒(400)。
     *
     * <p>warehouseType 在 DictReferenceRegistry 中注册,且 Warehouse 表有引用数据。</p>
     */
    @Test
    @Order(5)
    void disableReferencedTypeRejected() {
        // 插入一个 warehouseType 下的字典项,并让 Warehouse 表引用它
        String refKey = "refwk" + System.nanoTime();
        insertDictItem("warehouseType", refKey, "引用值", 1, 0);
        jdbcTemplate.update(
                "INSERT INTO \"warehouse\" (\"warehouse_code\",\"warehouse_name\","
                        + "\"warehouse_type\",\"enable_batch\",\"enable_expiry\","
                        + "\"enable_serial\",\"enable_location\",\"status\","
                        + "\"created_at\") VALUES (?, ?, ?, false, false, false, false, 1, NOW())",
                "WH-CHK-" + System.nanoTime(), "检查仓", refKey);
        // 停用 warehouseType 类型(该类型下有被引用的项)
        DictTypeUpdateDTO dto = new DictTypeUpdateDTO("仓库类型", "", 0);
        BizException ex = assertThrows(BizException.class,
                () -> dictTypeAdminService.update("warehouseType", dto));
        assertTrue(ex.getMessage().contains("引用"),
                "期望包含'引用',实际: " + ex.getMessage());
    }

    /**
     * 用例 6:仅改 typeName 不影响其他字段。
     */
    @Test
    @Order(6)
    void updateTypeNameOnly() {
        DictTypeUpdateDTO dto = new DictTypeUpdateDTO("改后名称", null, null);
        DictTypeVO vo = dictTypeAdminService.update("testtypeb", dto);
        assertEquals("改后名称", vo.typeName());
        assertEquals("备注B", vo.remark());
        assertEquals(1, vo.status());
    }

    /**
     * 用例 7:GET /dicts/types 返回全部类型含停用,带 enabledCount。
     */
    @Test
    @Order(7)
    void listTypesReturnsAll() {
        List<DictTypeVO> types = dictTypeAdminService.listAll();
        assertTrue(types.size() >= 3);
        DictTypeVO a = types.stream()
                .filter(t -> "testtypea".equals(t.typeCode()))
                .findFirst().orElseThrow();
        assertEquals(2L, a.enabledCount());
    }

    /**
     * 造字典类型。
     */
    private void insertType(String code, String name, String remark, int status) {
        DictTypeDO d = new DictTypeDO();
        d.setTypeCode(code);
        d.setTypeName(name);
        d.setRemark(remark);
        d.setStatus(status);
        d.setCreatedAt(java.time.LocalDateTime.now());
        d.setUpdatedAt(java.time.LocalDateTime.now());
        dictTypeMapper.insert(d);
    }

    /**
     * 造字典项。
     */
    private void insertDictItem(String type, String key, String label, int order, int st) {
        DictDO d = new DictDO();
        d.setDictType(type);
        d.setDictKey(key);
        d.setDictLabel(label);
        d.setSortOrder(order);
        d.setStatus(st);
        dictMapper.insert(d);
    }
}
