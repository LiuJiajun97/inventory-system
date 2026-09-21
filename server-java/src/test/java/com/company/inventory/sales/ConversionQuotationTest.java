package com.company.inventory.sales;

import com.company.inventory.common.constant.QuotationStatus;
import com.company.inventory.common.exception.BizException;
import com.company.inventory.mapper.CustomerMapper;
import com.company.inventory.mapper.ItemMapper;
import com.company.inventory.mapper.SalesOrderMapper;
import com.company.inventory.mapper.SalesQuotationMapper;
import com.company.inventory.mapper.UserMapper;
import com.company.inventory.mapper.WarehouseMapper;
import com.company.inventory.model.dto.sales.SalesOrderCreateDTO;
import com.company.inventory.model.dto.sales.SalesOrderLineDTO;
import com.company.inventory.model.dto.sales.SalesQuotationCreateDTO;
import com.company.inventory.model.dto.sales.SalesQuotationLineDTO;
import com.company.inventory.model.entity.customer.CustomerDO;
import com.company.inventory.model.entity.item.ItemDO;
import com.company.inventory.model.entity.sales.SalesOrderDO;
import com.company.inventory.model.entity.sales.SalesQuotationDO;
import com.company.inventory.model.entity.user.UserDO;
import com.company.inventory.model.entity.warehouse.WarehouseDO;
import com.company.inventory.service.SalesOrderService;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 销售报价单 → 销售订单 转换测试(V25):refDocType='quotation' 时校验 + 置 converted。
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
class ConversionQuotationTest {

    @Autowired
    private SalesOrderService salesOrderService;
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
    private SalesOrderMapper salesOrderMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long customerId;
    private long itemId;
    private long warehouseId;
    private long creatorId;
    private long salespersonId;

    @BeforeEach
    void resetDocs() {
        jdbcTemplate.update("DELETE FROM sales_order_item");
        jdbcTemplate.update("DELETE FROM sales_order");
        jdbcTemplate.update("DELETE FROM sales_quotation_item");
        jdbcTemplate.update("DELETE FROM sales_quotation");
    }

    @BeforeAll
    void cleanDb() {
        String sql = "TRUNCATE \"sales_order_item\",\"sales_order\",\"sales_quotation_item\","
                + "\"sales_quotation\",\"customer\",\"item\",\"warehouse\",\"sys_user\" "
                + "RESTART IDENTITY CASCADE";
        jdbcTemplate.execute(sql);

        WarehouseDO wh = new WarehouseDO();
        wh.setWarehouseCode("CV-WH");
        wh.setWarehouseName("转换测试仓");
        wh.setWarehouseType("raw");
        wh.setEnableBatch(false);
        wh.setEnableExpiry(false);
        wh.setEnableSerial(false);
        wh.setEnableLocation(false);
        warehouseMapper.insert(wh);
        warehouseId = wh.getId();

        ItemDO item = new ItemDO();
        item.setItemCode("CV-IT");
        item.setItemName("转换测试物");
        item.setUnit("件");
        itemMapper.insert(item);
        itemId = item.getId();

        CustomerDO cu = new CustomerDO();
        cu.setCustomerCode("CV-CU");
        cu.setCustomerName("转换测试客户");
        cu.setStatus(1);
        customerMapper.insert(cu);
        customerId = cu.getId();

        UserDO creator = new UserDO();
        creator.setUsername("cv_creator");
        creator.setPasswordHash("$2a$10$dummy");
        creator.setName("报价方");
        creator.setRole("operator");
        creator.setStatus(1);
        userMapper.insert(creator);
        creatorId = creator.getId();

        UserDO salesperson = new UserDO();
        salesperson.setUsername("cv_salesperson");
        salesperson.setPasswordHash("$2a$10$dummy");
        salesperson.setName("销售员");
        salesperson.setRole("operator");
        salesperson.setStatus(1);
        userMapper.insert(salesperson);
        salespersonId = salesperson.getId();
    }

    /**
     * 用例 1:报价单 draft → 转换成功,订单 ref 三列填充,报价单置 converted。
     */
    @Test
    void quotationDraftConvertedToOrder() {
        SalesQuotationCreateDTO qDto = new SalesQuotationCreateDTO(
                LocalDate.now(), customerId, salespersonId, warehouseId,
                LocalDate.now().plusDays(7), "转换源",
                List.of(new SalesQuotationLineDTO(itemId, new BigDecimal("10"),
                        new BigDecimal("100.00"), null, new BigDecimal("13.00"), "r")));
        var qVo = quotationService.create(qDto, "cv_creator");

        SalesOrderCreateDTO oDto = new SalesOrderCreateDTO(
                LocalDate.now(), customerId, salespersonId, warehouseId,
                null, null, null, null, null, null, "转换订单",
                List.of(new SalesOrderLineDTO(itemId, new BigDecimal("10"),
                        null, new BigDecimal("100.00"), null, new BigDecimal("13.00"), "r")),
                "quotation", qVo.docNo(), qVo.id());
        var oVo = salesOrderService.create(oDto, "cv_converter");

        SalesOrderDO order = salesOrderMapper.selectById(oVo.id());
        assertNotNull(order.getRefDocType());
        assertEquals("quotation", order.getRefDocType());
        assertEquals(qVo.docNo(), order.getRefDocNo());
        assertEquals(qVo.id(), order.getRefDocId());

        SalesQuotationDO q = quotationMapper.selectById(qVo.id());
        assertEquals(QuotationStatus.CONVERTED, q.getStatus());
    }

    /**
     * 用例 2:报价单 sent → 转换成功。
     */
    @Test
    void quotationSentConverted() {
        long qid = quotationService.create(new SalesQuotationCreateDTO(
                LocalDate.now(), customerId, salespersonId, warehouseId,
                LocalDate.now().plusDays(7), "已发送转换",
                List.of(new SalesQuotationLineDTO(itemId, new BigDecimal("5"),
                        new BigDecimal("100.00"), null, new BigDecimal("13.00"), "r"))),
                "cv_creator").id();
        quotationService.markSent(qid, "cv_creator");
        salesOrderService.create(new SalesOrderCreateDTO(
                LocalDate.now(), customerId, salespersonId, warehouseId,
                null, null, null, null, null, null, "sent转换",
                List.of(new SalesOrderLineDTO(itemId, new BigDecimal("5"),
                        null, new BigDecimal("100.00"), null, new BigDecimal("13.00"), "r")),
                "quotation", "BJ-x", qid), "cv_converter");
        assertEquals(QuotationStatus.CONVERTED,
                quotationMapper.selectById(qid).getStatus());
    }

    /**
     * 用例 3:报价单已作废 → 转换被拒。
     */
    @Test
    void quotationVoidedRejected() {
        long qid = quotationService.create(new SalesQuotationCreateDTO(
                LocalDate.now(), customerId, salespersonId, warehouseId,
                LocalDate.now().plusDays(7), "作废源",
                List.of(new SalesQuotationLineDTO(itemId, new BigDecimal("5"),
                        new BigDecimal("100.00"), null, new BigDecimal("13.00"), "r"))),
                "cv_creator").id();
        quotationService.voidDoc(qid, "cv_creator");
        assertThrows(BizException.class, () ->
                salesOrderService.create(new SalesOrderCreateDTO(
                        LocalDate.now(), customerId, salespersonId, warehouseId,
                        null, null, null, null, null, null, "拒转换",
                        List.of(new SalesOrderLineDTO(itemId, new BigDecimal("5"),
                                null, new BigDecimal("100.00"), null,
                                new BigDecimal("13.00"), "r")),
                        "quotation", "BJ-x", qid), "cv_converter"));
    }

    /**
     * 用例 4:报价单已过期 → 转换被拒。
     */
    @Test
    void quotationExpiredRejected() {
        long qid = quotationService.create(new SalesQuotationCreateDTO(
                LocalDate.now(), customerId, salespersonId, warehouseId,
                LocalDate.now().minusDays(1), "过期源",
                List.of(new SalesQuotationLineDTO(itemId, new BigDecimal("5"),
                        new BigDecimal("100.00"), null, new BigDecimal("13.00"), "r"))),
                "cv_creator").id();
        assertThrows(BizException.class, () ->
                salesOrderService.create(new SalesOrderCreateDTO(
                        LocalDate.now(), customerId, salespersonId, warehouseId,
                        null, null, null, null, null, null, "拒转换",
                        List.of(new SalesOrderLineDTO(itemId, new BigDecimal("5"),
                                null, new BigDecimal("100.00"), null,
                                new BigDecimal("13.00"), "r")),
                        "quotation", "BJ-x", qid), "cv_converter"));
    }

    /**
     * 用例 5:同一报价单被第二个订单引用 → 重复转换被拒。
     */
    @Test
    void quotationDuplicateReferenceRejected() {
        SalesQuotationCreateDTO qDto = new SalesQuotationCreateDTO(
                LocalDate.now(), customerId, salespersonId, warehouseId,
                LocalDate.now().plusDays(7), "重复源",
                List.of(new SalesQuotationLineDTO(itemId, new BigDecimal("5"),
                        new BigDecimal("100.00"), null, new BigDecimal("13.00"), "r")));
        var qVo = quotationService.create(qDto, "cv_creator");
        SalesOrderCreateDTO oDto = new SalesOrderCreateDTO(
                LocalDate.now(), customerId, salespersonId, warehouseId,
                null, null, null, null, null, null, "首单",
                List.of(new SalesOrderLineDTO(itemId, new BigDecimal("5"),
                        null, new BigDecimal("100.00"), null, new BigDecimal("13.00"), "r")),
                "quotation", qVo.docNo(), qVo.id());
        salesOrderService.create(oDto, "cv_converter");
        assertThrows(BizException.class, () ->
                salesOrderService.create(oDto, "cv_converter"));
    }
}