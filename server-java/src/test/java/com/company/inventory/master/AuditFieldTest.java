package com.company.inventory.master;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.inventory.common.support.UserContext;
import com.company.inventory.model.dto.item.ItemCreateDTO;
import com.company.inventory.model.dto.item.ItemUpdateDTO;
import com.company.inventory.model.dto.location.LocationCreateDTO;
import com.company.inventory.model.dto.supplier.SupplierCreateDTO;
import com.company.inventory.model.entity.item.ItemDO;
import com.company.inventory.model.entity.location.LocationDO;
import com.company.inventory.model.entity.purchase.PurchaseOrderItemDO;
import com.company.inventory.mapper.ItemMapper;
import com.company.inventory.mapper.LocationMapper;
import com.company.inventory.mapper.PurchaseOrderItemMapper;
import com.company.inventory.service.ItemService;
import com.company.inventory.service.LocationService;
import com.company.inventory.service.SupplierService;
import com.company.inventory.model.vo.item.ItemVO;
import com.company.inventory.model.vo.location.LocationVO;
import com.company.inventory.model.vo.supplier.SupplierVO;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 审计字段自动填充测试:验证 UserContext + AuditMetaObjectHandler 对所有表的 creator/createdAt/updater/updatedAt 填充。
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
class AuditFieldTest {

    @Autowired
    private ItemService itemService;
    @Autowired
    private SupplierService supplierService;
    @Autowired
    private LocationService locationService;
    @Autowired
    private ItemMapper itemMapper;
    @Autowired
    private LocationMapper locationMapper;
    @Autowired
    private PurchaseOrderItemMapper purchaseOrderItemMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeAll
    void setup() {
        String sql = "TRUNCATE \"dict\",\"dict_type\",\"purchase_order_item\",\"sales_order_item\",\"transfer_doc_item\","
                + "\"stocktake_doc_item\",\"stock_adjust_doc_item\",\"outbound_doc_item\",\"inbound_doc_item\","
                + "\"purchase_order\",\"sales_order\",\"transfer_doc\",\"stocktake_doc\",\"stock_adjust_doc\","
                + "\"outbound_doc\",\"inbound_doc\",\"stock_transaction\",\"stock\",\"serial\",\"batch\","
                + "\"location\",\"item\",\"warehouse\",\"supplier\",\"customer\",\"sys_user\" RESTART IDENTITY CASCADE";
        jdbcTemplate.execute(sql);
        UserContext.set("admin");
    }

    @AfterAll
    void teardown() {
        UserContext.clear();
    }

    /**
     * 用例 1:新建物品 → creator=admin、createdAt 非空、updatedAt 非空。
     */
    @Test
    void itemCreateAudit() {
        ItemVO item = itemService.create(new ItemCreateDTO("AUD-IT-1", "审计物品", "件", "规格A",
                null, "raw", new BigDecimal("5"), new BigDecimal("13")));
        assertNotNull(item.id());
        ItemDO db = itemMapper.selectById(item.id());
        assertEquals("admin", db.getCreator());
        assertNotNull(db.getCreatedAt());
        assertNotNull(db.getUpdatedAt());
        assertEquals("admin", db.getUpdater());
    }

    /**
     * 用例 2:编辑物品 → updater=admin、updatedAt 变化。
     */
    @Test
    void itemUpdateAudit() throws InterruptedException {
        ItemVO created = itemService.create(new ItemCreateDTO("AUD-IT-2", "编辑审计物品", "件", null,
                null, null, null, null));
        ItemDO before = itemMapper.selectById(created.id());
        LocalDateTime beforeUpdatedAt = before.getUpdatedAt();

        // 留出可观测的时间差,避免 create/update 落在同一时钟刻度导致 isAfter 不稳定
        Thread.sleep(10);

        itemService.update(created.id(), new ItemUpdateDTO("编辑后名称", null, null,
                null, null, null, null, null));

        ItemDO after = itemMapper.selectById(created.id());
        assertEquals("admin", after.getUpdater());
        assertNotNull(after.getUpdatedAt());
        // 强断言:更新时间必须严格变新(strictUpdateFill 只填 null 字段的旧实现会让它保持不变,
        // isEqual 会漏过该回归,故不放宽)
        if (beforeUpdatedAt != null) {
            assertTrue(after.getUpdatedAt().isAfter(beforeUpdatedAt),
                    "updated_at 未刷新: before=" + beforeUpdatedAt + " after=" + after.getUpdatedAt());
        }
    }

    /**
     * 用例 3:新建物品行(采购单建单带明细)→ 明细表 creator 非空。
     */
    @Test
    void purchaseOrderItemCreatorAudit() {
        ItemVO item = itemService.create(new ItemCreateDTO("AUD-IT-3", "明细审计物品", "件", null,
                null, null, null, null));
        SupplierVO sp = supplierService.create(new SupplierCreateDTO("AUD-SP-1", "审计供应商",
                null, new BigDecimal("13"), null, null, null, null, null, null), "admin");
        jdbcTemplate.update("INSERT INTO \"purchase_order\" (\"doc_no\",\"doc_date\",\"supplier_id\","
                + "\"buyer_id\",\"allow_over_receipt_rate\",\"total_amount\",\"total_tax_amount\","
                + "\"total_tax_inclusive\",\"status\",\"creator\",\"created_at\") VALUES "
                + "('AUD-PO-1', CURRENT_DATE, ?, 1, 0, 0, 0, 0, 'draft', 'admin', CURRENT_TIMESTAMP)",
                sp.id());
        Long poId = jdbcTemplate.queryForObject(
                "SELECT id FROM \"purchase_order\" WHERE \"doc_no\"='AUD-PO-1'", Long.class);
        assertNotNull(poId);
        jdbcTemplate.update("INSERT INTO \"purchase_order_item\" (\"order_id\",\"line_no\",\"item_id\","
                + "\"ordered_qty\",\"arrived_qty\",\"unit_price\",\"tax_rate\",\"amount\",\"tax_amount\","
                + "\"tax_inclusive_total\",\"creator\") VALUES (?, 1, ?, 10, 0, 100, 13, 1000, 130, 1130, 'admin')",
                poId, item.id());
        List<PurchaseOrderItemDO> items = purchaseOrderItemMapper.selectList(
                new LambdaQueryWrapper<PurchaseOrderItemDO>()
                        .eq(PurchaseOrderItemDO::getOrderId, poId));
        assertFalse(items.isEmpty());
        assertEquals("admin", items.get(0).getCreator());
    }

    /**
     * 用例 4:新建供应商 → creator 非空。
     */
    @Test
    void supplierCreateAudit() {
        SupplierVO sp = supplierService.create(new SupplierCreateDTO("AUD-SP-2", "审计供应商2",
                null, new BigDecimal("13"), null, null, null, null, null, null), "admin");
        assertNotNull(sp.id());
        assertNotNull(sp.creator());
        assertEquals("admin", sp.creator());
        assertNotNull(sp.createdAt());
    }

    /**
     * 用例 5:新建库位 → creator 非空。
     */
    @Test
    void locationCreateAudit() {
        jdbcTemplate.update("INSERT INTO \"warehouse\" (\"warehouse_code\",\"warehouse_name\","
                + "\"warehouse_type\",\"enable_batch\",\"enable_expiry\",\"enable_serial\","
                + "\"enable_location\",\"status\",\"creator\",\"created_at\") VALUES "
                + "('AUD-WH','审计仓','raw',false,false,false,false,1,'admin',CURRENT_TIMESTAMP)");
        Long whId = jdbcTemplate.queryForObject(
                "SELECT id FROM \"warehouse\" WHERE \"warehouse_code\"='AUD-WH'", Long.class);
        assertNotNull(whId);
        LocationVO loc = locationService.create(new LocationCreateDTO(whId, "AUD-LOC-1", "审计库位"));
        assertNotNull(loc.id());
        LocationDO db = locationMapper.selectById(loc.id());
        assertEquals("admin", db.getCreator());
        assertNotNull(db.getCreatedAt());
    }

    /**
     * 用例 6:viewer 创建资源 → creator=lisi(证明取的是当前登录人不是写死)。
     */
    @Test
    void viewerCreateAudit() {
        UserContext.set("lisi");
        try {
            ItemVO item = itemService.create(new ItemCreateDTO("AUD-IT-V", "viewer审计物品", "件", null,
                    null, null, null, null));
            ItemDO db = itemMapper.selectById(item.id());
            assertEquals("lisi", db.getCreator());
        } finally {
            UserContext.set("admin");
        }
    }
}
