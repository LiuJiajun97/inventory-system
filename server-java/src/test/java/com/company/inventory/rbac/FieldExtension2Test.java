package com.company.inventory.rbac;

import com.company.inventory.model.dto.customer.CustomerCreateDTO;
import com.company.inventory.model.dto.inbound.InboundCreateDTO;
import com.company.inventory.model.dto.inbound.InboundLineDTO;
import com.company.inventory.model.dto.outbound.OutboundCreateDTO;
import com.company.inventory.model.dto.outbound.OutboundLineDTO;
import com.company.inventory.model.dto.purchase.PurchaseOrderCreateDTO;
import com.company.inventory.model.dto.purchase.PurchaseOrderLineDTO;
import com.company.inventory.model.dto.supplier.SupplierCreateDTO;
import com.company.inventory.model.dto.warehouse.WarehouseCreateDTO;
import com.company.inventory.model.entity.item.ItemDO;
import com.company.inventory.model.entity.supplier.SupplierDO;
import com.company.inventory.model.entity.user.UserDO;
import com.company.inventory.model.entity.warehouse.WarehouseDO;
import com.company.inventory.mapper.ItemMapper;
import com.company.inventory.mapper.SupplierMapper;
import com.company.inventory.mapper.UserMapper;
import com.company.inventory.mapper.WarehouseMapper;
import com.company.inventory.service.CustomerService;
import com.company.inventory.service.InboundService;
import com.company.inventory.service.OutboundService;
import com.company.inventory.service.PurchaseOrderService;
import com.company.inventory.service.SupplierService;
import com.company.inventory.service.WarehouseService;
import com.company.inventory.model.vo.customer.CustomerVO;
import com.company.inventory.model.vo.inbound.InboundDocCreatedVO;
import com.company.inventory.model.vo.inbound.InboundDocVO;
import com.company.inventory.model.vo.outbound.OutboundDocCreatedVO;
import com.company.inventory.model.vo.outbound.OutboundDocVO;
import com.company.inventory.model.vo.purchase.PurchaseOrderVO;
import com.company.inventory.model.vo.supplier.SupplierVO;
import com.company.inventory.model.vo.warehouse.WarehouseVO;

import org.junit.jupiter.api.BeforeAll;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V10 主流系统字段对标测试:入库/出库行金额快照与行号、头总金额、默认仓排他、
 * 供应商/客户邮箱、采购单折扣/币种/汇率回读。自造数据自清理,不依赖其他用例。
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
class FieldExtension2Test {

    /** 供应商服务 */
    @Autowired
    private SupplierService supplierService;
    /** 客户服务 */
    @Autowired
    private CustomerService customerService;
    /** 入库服务 */
    @Autowired
    private InboundService inboundService;
    /** 出库服务 */
    @Autowired
    private OutboundService outboundService;
    /** 仓库服务 */
    @Autowired
    private WarehouseService warehouseService;
    /** 采购订单服务 */
    @Autowired
    private PurchaseOrderService purchaseOrderService;
    /** 物品 Mapper */
    @Autowired
    private ItemMapper itemMapper;
    /** 供应商 Mapper */
    @Autowired
    private SupplierMapper supplierMapper;
    /** 用户 Mapper */
    @Autowired
    private UserMapper userMapper;
    /** 仓库 Mapper */
    @Autowired
    private WarehouseMapper warehouseMapper;
    /** JDBC(清库) */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 前置:清库(本类自造数据自清理)。
     */
    @BeforeAll
    void cleanDb() {
        String sql = "TRUNCATE \"purchase_order_item\",\"sales_order_item\",\"transfer_doc_item\","
                + "\"stocktake_doc_item\",\"stock_adjust_doc_item\",\"outbound_doc_item\",\"inbound_doc_item\","
                + "\"purchase_order\",\"sales_order\",\"transfer_doc\",\"stocktake_doc\",\"stock_adjust_doc\","
                + "\"outbound_doc\",\"inbound_doc\",\"stock_transaction\",\"stock\",\"serial\",\"batch\","
                + "\"location\",\"item\",\"warehouse\",\"supplier\",\"customer\",\"sys_user\" RESTART IDENTITY CASCADE";
        jdbcTemplate.execute(sql);
    }

    /**
     * 用例 1:入库单创建后行金额快照(行号连号/金额/税额/价税合计)与头总金额。
     */
    @Test
    void inboundLineSnapshotAndTotal() {
        long whId = setupPlainWarehouse("V10-WH-1");
        ItemDO item = insertItem("V10-IT-1");

        // 行 1:2 × 10.50 = 21,税 = 21 × 10% = 2.10,价税合计 23.10
        // 行 2:3 × 空单价(按 0) = 0,税率空按 0 → 税额 0
        InboundLineDTO line1 = new InboundLineDTO(item.getId(), new BigDecimal("2"), null, null,
                null, null, null, null, new BigDecimal("10.50"), new BigDecimal("10"), null);
        InboundLineDTO line2 = new InboundLineDTO(item.getId(), new BigDecimal("3"), null, null,
                null, null, null, null, null, null, null);
        InboundDocCreatedVO in = inboundService.create(new InboundCreateDTO(whId, "V10 入库",
                List.of(line1, line2), null, null, LocalDate.now(), null, null, null,
                "采购入库", "经办小王"), "v10_test");

        InboundDocVO vo = inboundService.get(in.id());
        assertEquals("采购入库", vo.docType());
        assertEquals("经办小王", vo.handler());
        assertEquals(0, new BigDecimal("21.00").compareTo(new BigDecimal(vo.totalAmount())));
        assertEquals(2, vo.items().size());
        assertEquals(Integer.valueOf(1), vo.items().get(0).lineNo());
        assertEquals(Integer.valueOf(2), vo.items().get(1).lineNo());
        assertEquals(0, new BigDecimal("21.00").compareTo(vo.items().get(0).amount()));
        assertEquals(0, new BigDecimal("2.10").compareTo(vo.items().get(0).taxAmount()));
        assertEquals(0, new BigDecimal("23.10").compareTo(vo.items().get(0).taxInclusiveTotal()));
        assertEquals(0, BigDecimal.ZERO.compareTo(vo.items().get(1).amount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(vo.items().get(1).taxAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(vo.items().get(1).taxInclusiveTotal()));
    }

    /**
     * 用例 2:出库单创建后行金额快照(税率空按 0 算,税额为 0)与头总金额。
     */
    @Test
    void outboundLineSnapshotNullTaxRate() {
        long whId = setupPlainWarehouse("V10-WH-2");
        ItemDO item = insertItem("V10-IT-2");
        // 先入库备货 5 件(单价 12)
        InboundLineDTO inLine = new InboundLineDTO(item.getId(), new BigDecimal("5"), null, null,
                null, null, null, null, new BigDecimal("12"), null, null);
        inboundService.create(new InboundCreateDTO(whId, "V10 备货", List.of(inLine),
                null, null, LocalDate.now(), null, null, null, null, null), "v10_test");

        // 出库 5 件,税率空 → 税额 0,金额 = 5 × 12 = 60
        OutboundLineDTO outLine = new OutboundLineDTO(item.getId(), new BigDecimal("5"), null,
                null, null, new BigDecimal("12"), null, null);
        OutboundDocCreatedVO out = outboundService.create(new OutboundCreateDTO(whId, "V10 出库",
                List.of(outLine), null, null, LocalDate.now(), null, null, null,
                "销售出库", "经办小李"), "v10_test");

        OutboundDocVO vo = outboundService.get(out.id());
        assertEquals("销售出库", vo.docType());
        assertEquals("经办小李", vo.handler());
        assertEquals(0, new BigDecimal("60.00").compareTo(new BigDecimal(vo.totalAmount())));
        assertEquals(1, vo.items().size());
        assertEquals(Integer.valueOf(1), vo.items().get(0).lineNo());
        assertEquals(0, new BigDecimal("60.00").compareTo(vo.items().get(0).amount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(vo.items().get(0).taxAmount()));
        assertEquals(0, new BigDecimal("60.00").compareTo(vo.items().get(0).taxInclusiveTotal()));
    }

    /**
     * 用例 3:默认仓排他——A 默认 → B 默认后 A 非默认;C 不默认 → B 保持默认。
     */
    @Test
    void warehouseDefaultExclusive() {
        WarehouseVO a = warehouseService.create(new WarehouseCreateDTO("V10-WH-A", "V10 仓 A",
                "raw", false, false, false, false, true));
        assertTrue(Boolean.TRUE.equals(a.defaultWarehouse()));

        WarehouseVO b = warehouseService.create(new WarehouseCreateDTO("V10-WH-B", "V10 仓 B",
                "raw", false, false, false, false, true));
        assertTrue(Boolean.TRUE.equals(b.defaultWarehouse()));
        WarehouseVO aAfter = warehouseService.get(a.id());
        assertFalse(Boolean.TRUE.equals(aAfter.defaultWarehouse()));

        WarehouseVO c = warehouseService.create(new WarehouseCreateDTO("V10-WH-C", "V10 仓 C",
                "raw", false, false, false, false, null));
        assertFalse(Boolean.TRUE.equals(c.defaultWarehouse()));
        WarehouseVO bAfter = warehouseService.get(b.id());
        assertTrue(Boolean.TRUE.equals(bAfter.defaultWarehouse()));
    }

    /**
     * 用例 4:供应商/客户邮箱创建回读。
     */
    @Test
    void supplierCustomerEmail() {
        SupplierVO sp = supplierService.create(new SupplierCreateDTO("V10-SP-1", "V10 供应商",
                null, null, null, null, null, null, null, null, null, null, null,
                "sp@example.com", null), "v10_test");
        assertNotNull(sp.id());
        assertEquals("sp@example.com", supplierService.get(sp.id()).email());

        CustomerVO cus = customerService.create(new CustomerCreateDTO("V10-CUS-1", "V10 客户",
                null, null, null, null, null, null, null, null, null, null, null,
                "cus@example.com", null), "v10_test");
        assertNotNull(cus.id());
        assertEquals("cus@example.com", customerService.get(cus.id()).email());
    }

    /**
     * 用例 5:采购单折扣额/币种/汇率创建回读(不参与合计计算)。
     */
    @Test
    void purchaseOrderExtFields() {
        long supplierId = insertSupplier();
        long userId = insertUser();
        ItemDO item = insertItem("V10-IT-5");

        PurchaseOrderVO vo = purchaseOrderService.create(new PurchaseOrderCreateDTO(
                LocalDate.now(), supplierId, userId, null, null, null, null,
                new BigDecimal("50.00"), "USD", new BigDecimal("7.200000"), "V10 采购",
                List.of(new PurchaseOrderLineDTO(item.getId(), new BigDecimal("10"), null,
                        new BigDecimal("100"), new BigDecimal("13"), null))), "v10_test");
        assertNotNull(vo.id());
        assertEquals("USD", vo.currencyCode());
        assertEquals(0, new BigDecimal("50.00").compareTo(new BigDecimal(vo.discountAmount())));
        assertEquals(0, new BigDecimal("7.2").compareTo(new BigDecimal(vo.exchangeRate())));
        // 折扣额不参与合计:整单不含税合计仍为 10 × 100 = 1000
        assertEquals(0, new BigDecimal("1000.00").compareTo(new BigDecimal(vo.totalAmount())));
    }

    /**
     * 造普通仓库(全开关关闭,默认仓标记空)。
     *
     * @param code 仓库编码(唯一)
     * @return 仓库 ID
     */
    private long setupPlainWarehouse(String code) {
        WarehouseDO wh = new WarehouseDO();
        wh.setWarehouseCode(code);
        wh.setWarehouseName(code + " 仓");
        wh.setWarehouseType("raw");
        wh.setEnableBatch(false);
        wh.setEnableExpiry(false);
        wh.setEnableSerial(false);
        wh.setEnableLocation(false);
        warehouseMapper.insert(wh);
        return wh.getId();
    }

    /**
     * 造物品(唯一编码,单位"件")。
     *
     * @param code 物品编码
     * @return 物品实体
     */
    private ItemDO insertItem(String code) {
        ItemDO item = new ItemDO();
        item.setItemCode(code);
        item.setItemName(code + " 物品");
        item.setUnit("件");
        itemMapper.insert(item);
        return item;
    }

    /**
     * 造启用供应商。
     *
     * @return 供应商 ID
     */
    private long insertSupplier() {
        SupplierDO sp = new SupplierDO();
        sp.setSupplierCode("V10-SP-2");
        sp.setSupplierName("V10 采购供应商");
        sp.setStatus(1);
        supplierMapper.insert(sp);
        return sp.getId();
    }

    /**
     * 造 operator 用户(采购员)。
     *
     * @return 用户 ID
     */
    private long insertUser() {
        UserDO user = new UserDO();
        user.setUsername("v10_po_buyer");
        user.setPasswordHash("$2a$10$dummy");
        user.setName("V10 采购员");
        user.setRole("operator");
        user.setStatus(1);
        userMapper.insert(user);
        return user.getId();
    }
}
