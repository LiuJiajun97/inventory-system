package com.company.inventory.purchase;

import com.company.inventory.common.constant.RequisitionStatus;
import com.company.inventory.common.exception.BizException;
import com.company.inventory.mapper.DictMapper;
import com.company.inventory.mapper.ItemMapper;
import com.company.inventory.mapper.PurchaseOrderMapper;
import com.company.inventory.mapper.PurchaseRequisitionMapper;
import com.company.inventory.mapper.SupplierMapper;
import com.company.inventory.mapper.UserMapper;
import com.company.inventory.mapper.WarehouseMapper;
import com.company.inventory.model.dto.purchase.PurchaseOrderCreateDTO;
import com.company.inventory.model.dto.purchase.PurchaseOrderLineDTO;
import com.company.inventory.model.dto.purchase.PurchaseRequisitionCreateDTO;
import com.company.inventory.model.dto.purchase.PurchaseRequisitionLineDTO;
import com.company.inventory.model.entity.dict.DictDO;
import com.company.inventory.model.entity.item.ItemDO;
import com.company.inventory.model.entity.purchase.PurchaseOrderDO;
import com.company.inventory.model.entity.purchase.PurchaseRequisitionDO;
import com.company.inventory.model.entity.supplier.SupplierDO;
import com.company.inventory.model.entity.user.UserDO;
import com.company.inventory.model.entity.warehouse.WarehouseDO;
import com.company.inventory.service.PurchaseOrderService;
import com.company.inventory.service.PurchaseRequisitionService;

import jakarta.validation.constraints.NotNull;

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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 请购单 → 采购订单 转换测试(V25):refDocType='requisition' 时校验 + 置 converted。
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
class ConversionRequisitionTest {

    @Autowired
    private PurchaseOrderService purchaseOrderService;
    @Autowired
    private PurchaseRequisitionService requisitionService;
    @Autowired
    private DictMapper dictMapper;
    @Autowired
    private SupplierMapper supplierMapper;
    @Autowired
    private ItemMapper itemMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private WarehouseMapper warehouseMapper;
    @Autowired
    private PurchaseRequisitionMapper requisitionMapper;
    @Autowired
    private PurchaseOrderMapper purchaseOrderMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long warehouseId;
    private long itemId;
    private long applicantId;
    private long buyerId;
    private long supplierId;

    @BeforeEach
    void resetDocs() {
        jdbcTemplate.update("DELETE FROM purchase_order_item");
        jdbcTemplate.update("DELETE FROM purchase_order");
        jdbcTemplate.update("DELETE FROM purchase_requisition_item");
        jdbcTemplate.update("DELETE FROM purchase_requisition");
    }

    @BeforeAll
    void cleanDb() {
        String sql = "TRUNCATE \"purchase_order_item\",\"purchase_order\","
                + "\"purchase_requisition_item\",\"purchase_requisition\","
                + "\"supplier\",\"dict\",\"item\",\"warehouse\",\"sys_user\" "
                + "RESTART IDENTITY CASCADE";
        jdbcTemplate.execute(sql);

        DictDO deptType = new DictDO();
        deptType.setDictType("dept");
        deptType.setDictKey("dept");
        deptType.setDictLabel("部门");
        deptType.setSortOrder(0);
        deptType.setStatus(1);
        dictMapper.insert(deptType);
        DictDO production = new DictDO();
        production.setDictType("dept");
        production.setDictKey("production");
        production.setDictLabel("生产部");
        production.setSortOrder(1);
        production.setStatus(1);
        dictMapper.insert(production);

        SupplierDO supplier = new SupplierDO();
        supplier.setSupplierCode("CR-SU");
        supplier.setSupplierName("转换测试供应商");
        supplier.setStatus(1);
        supplierMapper.insert(supplier);
        supplierId = supplier.getId();

        WarehouseDO wh = new WarehouseDO();
        wh.setWarehouseCode("CR-WH");
        wh.setWarehouseName("转换测试仓");
        wh.setWarehouseType("raw");
        wh.setEnableBatch(false);
        wh.setEnableExpiry(false);
        wh.setEnableSerial(false);
        wh.setEnableLocation(false);
        warehouseMapper.insert(wh);
        warehouseId = wh.getId();

        ItemDO item = new ItemDO();
        item.setItemCode("CR-IT");
        item.setItemName("转换测试物");
        item.setUnit("件");
        itemMapper.insert(item);
        itemId = item.getId();

        UserDO applicant = new UserDO();
        applicant.setUsername("cr_applicant");
        applicant.setPasswordHash("$2a$10$dummy");
        applicant.setName("申请人");
        applicant.setRole("operator");
        applicant.setStatus(1);
        userMapper.insert(applicant);
        applicantId = applicant.getId();

        UserDO buyer = new UserDO();
        buyer.setUsername("cr_buyer");
        buyer.setPasswordHash("$2a$10$dummy");
        buyer.setName("采购员");
        buyer.setRole("operator");
        buyer.setStatus(1);
        userMapper.insert(buyer);
        buyerId = buyer.getId();
    }

    /**
     * 用例 1:请购单 draft → 转换成功,订单 ref 三列填充,请购单置 converted。
     */
    @Test
    void requisitionDraftConvertedToOrder() {
        PurchaseRequisitionCreateDTO rDto = new PurchaseRequisitionCreateDTO(
                LocalDate.now(), warehouseId, applicantId, "production", "转换源",
                List.of(new PurchaseRequisitionLineDTO(itemId, new BigDecimal("5"),
                        LocalDate.now().plusDays(7), new BigDecimal("100.00"), "r")));
        var rVo = requisitionService.create(rDto, "cr_applicant");

        PurchaseOrderCreateDTO oDto = new PurchaseOrderCreateDTO(
                LocalDate.now(), supplierId, buyerId, BigDecimal.ZERO,
                null, null, null, null, null, null, "转换订单",
                List.of(new PurchaseOrderLineDTO(itemId, new BigDecimal("5"),
                        null, new BigDecimal("100.00"), null, new BigDecimal("13.00"), "r")),
                "requisition", rVo.docNo(), rVo.id());
        var oVo = purchaseOrderService.create(oDto, "cr_converter");

        PurchaseOrderDO order = purchaseOrderMapper.selectById(oVo.id());
        assertEquals("requisition", order.getRefDocType());
        assertEquals(rVo.docNo(), order.getRefDocNo());
        assertEquals(rVo.id(), order.getRefDocId());

        PurchaseRequisitionDO r = requisitionMapper.selectById(rVo.id());
        assertEquals(RequisitionStatus.CONVERTED, r.getStatus());
    }

    /**
     * 用例 2:请购单 submitted → 转换成功。
     */
    @Test
    void requisitionSubmittedConverted() {
        long rid = requisitionService.create(new PurchaseRequisitionCreateDTO(
                LocalDate.now(), warehouseId, applicantId, "production", "已提交源",
                List.of(new PurchaseRequisitionLineDTO(itemId, new BigDecimal("5"),
                        LocalDate.now().plusDays(7), new BigDecimal("100.00"), "r"))),
                "cr_applicant").id();
        requisitionService.submit(rid, "cr_applicant");
        purchaseOrderService.create(new PurchaseOrderCreateDTO(
                LocalDate.now(), supplierId, buyerId, BigDecimal.ZERO,
                null, null, null, null, null, null, "submitted转换",
                List.of(new PurchaseOrderLineDTO(itemId, new BigDecimal("5"),
                        null, new BigDecimal("100.00"), null, new BigDecimal("13.00"), "r")),
                "requisition", "QG-x", rid), "cr_converter");
        assertEquals(RequisitionStatus.CONVERTED,
                requisitionMapper.selectById(rid).getStatus());
    }

    /**
     * 用例 3:请购单已取消 → 转换被拒。
     */
    @Test
    void requisitionCancelledRejected() {
        long rid = requisitionService.create(new PurchaseRequisitionCreateDTO(
                LocalDate.now(), warehouseId, applicantId, "production", "取消源",
                List.of(new PurchaseRequisitionLineDTO(itemId, new BigDecimal("5"),
                        LocalDate.now().plusDays(7), new BigDecimal("100.00"), "r"))),
                "cr_applicant").id();
        requisitionService.cancel(rid, "cr_applicant");
        assertThrows(BizException.class, () ->
                purchaseOrderService.create(new PurchaseOrderCreateDTO(
                        LocalDate.now(), supplierId, buyerId, BigDecimal.ZERO,
                        null, null, null, null, null, null, "拒转换",
                        List.of(new PurchaseOrderLineDTO(itemId, new BigDecimal("5"),
                                null, new BigDecimal("100.00"), null,
                                new BigDecimal("13.00"), "r")),
                        "requisition", "QG-x", rid), "cr_converter"));
    }

    /**
     * 用例 4:同一请购单被第二个订单引用 → 重复转换被拒。
     */
    @Test
    void requisitionDuplicateReferenceRejected() {
        PurchaseRequisitionCreateDTO rDto = new PurchaseRequisitionCreateDTO(
                LocalDate.now(), warehouseId, applicantId, "production", "重复源",
                List.of(new PurchaseRequisitionLineDTO(itemId, new BigDecimal("5"),
                        LocalDate.now().plusDays(7), new BigDecimal("100.00"), "r")));
        var rVo = requisitionService.create(rDto, "cr_applicant");
        PurchaseOrderCreateDTO oDto = new PurchaseOrderCreateDTO(
                LocalDate.now(), supplierId, buyerId, BigDecimal.ZERO,
                null, null, null, null, null, null, "首单",
                List.of(new PurchaseOrderLineDTO(itemId, new BigDecimal("5"),
                        null, new BigDecimal("100.00"), null, new BigDecimal("13.00"), "r")),
                "requisition", rVo.docNo(), rVo.id());
        purchaseOrderService.create(oDto, "cr_converter");
        assertThrows(BizException.class, () ->
                purchaseOrderService.create(oDto, "cr_converter"));
    }

    /**
     * 用例 5:不识别的 refDocType → 抛错。
     */
    @Test
    void invalidRefDocTypeRejected() {
        assertThrows(BizException.class, () ->
                purchaseOrderService.create(new PurchaseOrderCreateDTO(
                        LocalDate.now(), supplierId, buyerId, BigDecimal.ZERO,
                        null, null, null, null, null, null, "非法ref",
                        List.of(new PurchaseOrderLineDTO(itemId, new BigDecimal("5"),
                                null, new BigDecimal("100.00"), null,
                                new BigDecimal("13.00"), "r")),
                        "unknown_type", "x", 1L), "cr_converter"));
    }

    /**
     * 占位,避免 import 静态分析告警(jakarta.validation.constraints.NotNull 用)。
     */
    @SuppressWarnings("unused")
    private static void touch(NotNull ignored) {
        // no-op
    }
}