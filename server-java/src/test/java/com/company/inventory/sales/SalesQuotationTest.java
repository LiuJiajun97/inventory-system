package com.company.inventory.sales;

import com.company.inventory.common.constant.ErrorCode;
import com.company.inventory.common.exception.BizException;
import com.company.inventory.common.support.DataScope;
import com.company.inventory.mapper.CustomerMapper;
import com.company.inventory.mapper.ItemMapper;
import com.company.inventory.mapper.SalesQuotationMapper;
import com.company.inventory.mapper.UserMapper;
import com.company.inventory.mapper.WarehouseMapper;
import com.company.inventory.model.dto.sales.SalesQuotationCreateDTO;
import com.company.inventory.model.dto.sales.SalesQuotationLineDTO;
import com.company.inventory.model.entity.customer.CustomerDO;
import com.company.inventory.model.entity.item.ItemDO;
import com.company.inventory.model.entity.sales.SalesQuotationDO;
import com.company.inventory.model.entity.user.UserDO;
import com.company.inventory.model.entity.warehouse.WarehouseDO;
import com.company.inventory.model.vo.sales.SalesQuotationVO;
import com.company.inventory.service.SalesQuotationService;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 销售报价单测试(V25):状态机 draft/sent/converted/voided + 价税重算 + 过期标记。
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
class SalesQuotationTest {

    @Autowired
    private SalesQuotationService quotationService;
    @Autowired
    private CustomerMapper customerMapper;
    @Autowired
    private ItemMapper itemMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private WarehouseMapper warehouseMapper;
    @Autowired
    private SalesQuotationMapper quotationMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 客户 ID。 */
    private long customerId;
    /** 物品 ID。 */
    private long itemId;
    /** 仓库 ID。 */
    private long warehouseId;
    /** 制单人用户 ID。 */
    private long creatorUserId;

    /** 单号种子。 */
    private long seq = 0;

    @BeforeEach
    void resetQuotations() {
        // 用例间隔离:每次清掉报价单表(避免状态冲突)
        jdbcTemplate.update("DELETE FROM sales_quotation_item");
        jdbcTemplate.update("DELETE FROM sales_quotation");
    }

    @BeforeAll
    void cleanDb() {
        String sql = "TRUNCATE \"sales_quotation_item\",\"sales_quotation\",\"purchase_requisition_item\","
                + "\"purchase_requisition\",\"customer\",\"item\",\"warehouse\",\"sys_user\" "
                + "RESTART IDENTITY CASCADE";
        jdbcTemplate.execute(sql);

        WarehouseDO wh = new WarehouseDO();
        wh.setWarehouseCode("QT-WH");
        wh.setWarehouseName("报价测试仓");
        wh.setWarehouseType("raw");
        wh.setEnableBatch(false);
        wh.setEnableExpiry(false);
        wh.setEnableSerial(false);
        wh.setEnableLocation(false);
        warehouseMapper.insert(wh);
        warehouseId = wh.getId();

        ItemDO item = new ItemDO();
        item.setItemCode("QT-IT");
        item.setItemName("报价测试物");
        item.setUnit("件");
        item.setDefaultTaxRate(new BigDecimal("13.00"));
        itemMapper.insert(item);
        itemId = item.getId();

        CustomerDO cu = new CustomerDO();
        cu.setCustomerCode("QT-CU");
        cu.setCustomerName("报价测试客户");
        cu.setStatus(1);
        customerMapper.insert(cu);
        customerId = cu.getId();

        UserDO user = new UserDO();
        user.setUsername("qt_creator");
        user.setPasswordHash("$2a$10$dummy");
        user.setName("报价制单人");
        user.setRole("operator");
        user.setStatus(1);
        userMapper.insert(user);
        creatorUserId = user.getId();
    }

    /**
     * 用例 1:新建报价单 → 状态 draft,服务端价税重算。
     */
    @Test
    void createDraftQuotation() {
        SalesQuotationCreateDTO dto = newDto(LocalDate.now(), null);
        SalesQuotationVO vo = quotationService.create(dto, "qt_creator");
        assertEquals("draft", vo.status());
        assertNull(vo.expired());
        // 数量 10 × 不含税单价 100 = 1000;税率 13% → 税额 130;价税合计 1130
        SalesQuotationDO row = quotationMapper.selectById(vo.id());
        assertEquals(0, new BigDecimal("1000.0000").compareTo(row.getTotalAmount()));
        assertEquals(0, new BigDecimal("130.0000").compareTo(row.getTotalTaxAmount()));
        assertEquals(0, new BigDecimal("1130.0000").compareTo(row.getTotalTaxInclusive()));
    }

    /**
     * 用例 2:仅含税单价 + 服务端反算(服务端不信前端金额)。
     */
    @Test
    void createFromTaxPriceRecalc() {
        SalesQuotationCreateDTO dto = new SalesQuotationCreateDTO(
                LocalDate.now(), customerId, creatorUserId, warehouseId,
                LocalDate.now().plusDays(7), "含税路径测试",
                List.of(new SalesQuotationLineDTO(itemId, new BigDecimal("10"),
                        null, new BigDecimal("113.00"), new BigDecimal("13.00"), "remark")));
        SalesQuotationVO vo = quotationService.create(dto, "qt_creator");
        SalesQuotationDO row = quotationMapper.selectById(vo.id());
        assertEquals(0, new BigDecimal("1000.0000").compareTo(row.getTotalAmount()));
        assertEquals(0, new BigDecimal("130.0000").compareTo(row.getTotalTaxAmount()));
        assertEquals(0, new BigDecimal("1130.0000").compareTo(row.getTotalTaxInclusive()));
    }

    /**
     * 用例 3:markSent 仅 draft 可用;sent 状态二次 markSent 抛错。
     */
    @Test
    void markSentOnlyFromDraft() {
        long id = quotationService.create(newDto(LocalDate.now(), null), "qt_creator").id();
        assertEquals("sent", quotationService.markSent(id, "qt_creator").status());
        assertThrows(BizException.class, () -> quotationService.markSent(id, "qt_creator"));
    }

    /**
     * 用例 4:voidDoc 仅 draft/sent 可用;converted/voided 拒绝。
     */
    @Test
    void voidOnlyFromDraftOrSent() {
        long id = quotationService.create(newDto(LocalDate.now(), null), "qt_creator").id();
        quotationService.markSent(id, "qt_creator");
        assertEquals("voided", quotationService.voidDoc(id, "qt_creator").status());
        // 二次作废拒绝
        assertThrows(BizException.class, () -> quotationService.voidDoc(id, "qt_creator"));
    }

    /**
     * 用例 5:update 仅 draft 可用;sent 后编辑抛错。
     */
    @Test
    void updateOnlyDraft() {
        long id = quotationService.create(newDto(LocalDate.now(), null), "qt_creator").id();
        quotationService.markSent(id, "qt_creator");
        assertThrows(BizException.class, () ->
                quotationService.update(id, newDto(LocalDate.now(), null), "qt_creator"));
    }

    /**
     * 用例 6:过期展示标记 — 报价有效期 < 今天 且状态 draft/sent 时为 true;voided/converted 为 false。
     */
    @Test
    void expiredDisplayFlag() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        SalesQuotationCreateDTO dto = new SalesQuotationCreateDTO(
                LocalDate.now(), customerId, creatorUserId, warehouseId,
                yesterday, "过期测试", List.of(line()));
        SalesQuotationVO vo = quotationService.create(dto, "qt_creator");
        assertNotNull(vo.expired());
        assertTrue(Boolean.TRUE.equals(vo.expired()));
    }

    /**
     * 用例 7:未过期时 expired=null(避免无意义的展示)。
     */
    @Test
    void notExpiredWhenValidUntilFuture() {
        SalesQuotationCreateDTO dto = new SalesQuotationCreateDTO(
                LocalDate.now(), customerId, creatorUserId, warehouseId,
                LocalDate.now().plusDays(30), "未来有效期",
                List.of(line()));
        SalesQuotationVO vo = quotationService.create(dto, "qt_creator");
        assertNull(vo.expired());
    }

    private SalesQuotationCreateDTO newDto(LocalDate date, LocalDate validUntil) {
        return new SalesQuotationCreateDTO(date, customerId, creatorUserId, warehouseId,
                validUntil, "单据备注", List.of(line()));
    }

    private SalesQuotationLineDTO line() {
        return new SalesQuotationLineDTO(itemId, new BigDecimal("10"),
                new BigDecimal("100.00"), null, new BigDecimal("13.00"), "remark");
    }

    /**
     * 用例 8:数据权限——非 admin 授权仓不含发货仓 → 详情 403/列表查空;授权命中 → 放行。
     */
    @Test
    void dataScopeByDocId() {
        SalesQuotationVO vo = quotationService.create(newDto(LocalDate.now(), null), "qt_creator");
        try {
            DataScope.set(List.of(999999L));
            BizException ex = assertThrows(BizException.class, () -> quotationService.get(vo.id()));
            assertEquals(ErrorCode.FORBIDDEN, ex.getCode(), "无权仓库应 403 而非 200");
            assertEquals(403, ex.getStatus());
            assertEquals(0L, quotationService.list(new com.company.inventory.model.query.SalesQuotationQuery()).total());
            // 发货仓在授权列表 → 放行
            DataScope.set(List.of(warehouseId));
            assertNotNull(quotationService.get(vo.id()));
            assertTrue(quotationService.list(new com.company.inventory.model.query.SalesQuotationQuery()).total() >= 1);
        } finally {
            DataScope.clear();
        }
    }
}