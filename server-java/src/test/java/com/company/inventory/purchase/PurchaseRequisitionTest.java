package com.company.inventory.purchase;

import com.company.inventory.common.constant.ErrorCode;
import com.company.inventory.common.exception.BizException;
import com.company.inventory.common.support.DataScope;
import com.company.inventory.mapper.DictMapper;
import com.company.inventory.mapper.ItemMapper;
import com.company.inventory.mapper.PurchaseRequisitionMapper;
import com.company.inventory.mapper.UserMapper;
import com.company.inventory.mapper.WarehouseMapper;
import com.company.inventory.model.dto.purchase.PurchaseRequisitionCreateDTO;
import com.company.inventory.model.dto.purchase.PurchaseRequisitionLineDTO;
import com.company.inventory.model.entity.dict.DictDO;
import com.company.inventory.model.entity.item.ItemDO;
import com.company.inventory.model.entity.purchase.PurchaseRequisitionDO;
import com.company.inventory.model.entity.user.UserDO;
import com.company.inventory.model.entity.warehouse.WarehouseDO;
import com.company.inventory.model.vo.purchase.PurchaseRequisitionVO;
import com.company.inventory.service.PurchaseRequisitionService;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 请购单测试(V25):状态机 draft/submitted/converted/cancelled + 无金额合计 + 申请部门字典校验。
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
class PurchaseRequisitionTest {

    @Autowired
    private PurchaseRequisitionService requisitionService;
    @Autowired
    private DictMapper dictMapper;
    @Autowired
    private ItemMapper itemMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private WarehouseMapper warehouseMapper;
    @Autowired
    private PurchaseRequisitionMapper requisitionMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long warehouseId;
    private long itemId;
    private long applicantId;

    @BeforeEach
    void resetRequisitions() {
        jdbcTemplate.update("DELETE FROM purchase_requisition_item");
        jdbcTemplate.update("DELETE FROM purchase_requisition");
    }

    @BeforeAll
    void cleanDb() {
        String sql = "TRUNCATE \"purchase_requisition_item\",\"purchase_requisition\","
                + "\"dict\",\"item\",\"warehouse\",\"sys_user\" RESTART IDENTITY CASCADE";
        jdbcTemplate.execute(sql);

        // 字典 dept seed(seed.sql 已被 truncate,自建最小集)
        DictDO type = new DictDO();
        type.setDictType("dept");
        type.setDictKey("dept");
        type.setDictLabel("部门");
        type.setSortOrder(0);
        type.setStatus(1);
        dictMapper.insert(type);
        DictDO production = new DictDO();
        production.setDictType("dept");
        production.setDictKey("production");
        production.setDictLabel("生产部");
        production.setSortOrder(1);
        production.setStatus(1);
        dictMapper.insert(production);

        WarehouseDO wh = new WarehouseDO();
        wh.setWarehouseCode("QG-WH");
        wh.setWarehouseName("请购测试仓");
        wh.setWarehouseType("raw");
        wh.setEnableBatch(false);
        wh.setEnableExpiry(false);
        wh.setEnableSerial(false);
        wh.setEnableLocation(false);
        warehouseMapper.insert(wh);
        warehouseId = wh.getId();

        ItemDO item = new ItemDO();
        item.setItemCode("QG-IT");
        item.setItemName("请购测试物");
        item.setUnit("件");
        itemMapper.insert(item);
        itemId = item.getId();

        UserDO user = new UserDO();
        user.setUsername("qg_creator");
        user.setPasswordHash("$2a$10$dummy");
        user.setName("请购申请人");
        user.setRole("operator");
        user.setStatus(1);
        userMapper.insert(user);
        applicantId = user.getId();
    }

    /**
     * 用例 1:新建请购单 → draft,无金额合计(行价可空不汇总)。
     */
    @Test
    void createDraftRequisition() {
        PurchaseRequisitionCreateDTO dto = newDto("production", null);
        PurchaseRequisitionVO vo = requisitionService.create(dto, "qg_creator");
        assertEquals("draft", vo.status());
        // 请购单头无金额合计字段,仅行携带 quantity/unitPrice
        PurchaseRequisitionDO row = requisitionMapper.selectById(vo.id());
        assertEquals("production", row.getDepartment());
        assertEquals(applicantId, row.getApplicantId());
    }

    /**
     * 用例 2:行价可空 → 不报错;参考单价留空不影响落库。
     */
    @Test
    void linePriceOptional() {
        PurchaseRequisitionCreateDTO dto = new PurchaseRequisitionCreateDTO(
                LocalDate.now(), warehouseId, applicantId, "production", "无单价",
                List.of(new PurchaseRequisitionLineDTO(itemId, new BigDecimal("5"),
                        LocalDate.now().plusDays(7), null, "row")));
        PurchaseRequisitionVO vo = requisitionService.create(dto, "qg_creator");
        assertEquals(1, vo.items().size());
        assertNull(vo.items().get(0).unitPrice());
    }

    /**
     * 用例 3:submit 仅 draft 可用;submitted 二次 submit 抛错。
     */
    @Test
    void submitOnlyDraft() {
        long id = requisitionService.create(newDto(null, null), "qg_creator").id();
        assertEquals("submitted", requisitionService.submit(id, "qg_creator").status());
        assertThrows(BizException.class, () -> requisitionService.submit(id, "qg_creator"));
    }

    /**
     * 用例 4:cancel draft/submitted → cancelled;cancelled 二次取消抛错。
     */
    @Test
    void cancelFromDraftOrSubmitted() {
        long id = requisitionService.create(newDto(null, null), "qg_creator").id();
        requisitionService.submit(id, "qg_creator");
        assertEquals("cancelled", requisitionService.cancel(id, "qg_creator").status());
        assertThrows(BizException.class, () -> requisitionService.cancel(id, "qg_creator"));
    }

    /**
     * 用例 5:update 仅 draft 可用。
     */
    @Test
    void updateOnlyDraft() {
        long id = requisitionService.create(newDto(null, null), "qg_creator").id();
        requisitionService.submit(id, "qg_creator");
        assertThrows(BizException.class, () ->
                requisitionService.update(id, newDto(null, "改备注"), "qg_creator"));
    }

    /**
     * 用例 6:申请部门不在字典 dept → 抛错。
     */
    @Test
    void invalidDepartmentRejected() {
        PurchaseRequisitionCreateDTO dto = new PurchaseRequisitionCreateDTO(
                LocalDate.now(), warehouseId, applicantId, "invalid_dept", "无此部门",
                List.of(line()));
        assertThrows(BizException.class, () -> requisitionService.create(dto, "qg_creator"));
    }

    /**
     * 用例 8:数据权限——非 admin 授权仓不含收货仓 → 详情 403/列表查空;授权命中 → 放行。
     */
    @Test
    void dataScopeByDocId() {
        PurchaseRequisitionVO vo = requisitionService.create(newDto(null, null), "qg_creator");
        try {
            DataScope.set(List.of(999999L));
            BizException ex = assertThrows(BizException.class, () -> requisitionService.get(vo.id()));
            assertEquals(ErrorCode.FORBIDDEN, ex.getCode(), "无权仓库应 403 而非 200");
            assertEquals(403, ex.getStatus());
            assertEquals(0L, requisitionService.list(new com.company.inventory.model.query.PurchaseRequisitionQuery()).total());
            // 收货仓在授权列表 → 放行
            DataScope.set(List.of(warehouseId));
            assertNotNull(requisitionService.get(vo.id()));
            assertEquals(1L, requisitionService.list(new com.company.inventory.model.query.PurchaseRequisitionQuery()).total());
        } finally {
            DataScope.clear();
        }
    }

    private PurchaseRequisitionCreateDTO newDto(String dept, String remark) {
        return new PurchaseRequisitionCreateDTO(LocalDate.now(), warehouseId, applicantId,
                dept, remark == null ? "默认备注" : remark, List.of(line()));
    }

    private PurchaseRequisitionLineDTO line() {
        return new PurchaseRequisitionLineDTO(itemId, new BigDecimal("5"),
                LocalDate.now().plusDays(7), new BigDecimal("100.00"), "row");
    }
}