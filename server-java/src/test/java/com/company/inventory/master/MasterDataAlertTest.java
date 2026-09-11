package com.company.inventory.master;

import com.company.inventory.common.exception.BizException;
import com.company.inventory.common.page.PageResult;
import com.company.inventory.model.dto.customer.CustomerCreateDTO;
import com.company.inventory.model.dto.customer.CustomerUpdateDTO;
import com.company.inventory.model.dto.item.ItemCreateDTO;
import com.company.inventory.model.dto.item.ItemUpdateDTO;
import com.company.inventory.model.dto.supplier.SupplierCreateDTO;
import com.company.inventory.model.dto.supplier.SupplierUpdateDTO;
import com.company.inventory.model.entity.item.ItemDO;
import com.company.inventory.model.entity.stock.BatchDO;
import com.company.inventory.model.entity.stock.StockDO;
import com.company.inventory.model.entity.warehouse.WarehouseDO;
import com.company.inventory.mapper.BatchMapper;
import com.company.inventory.mapper.CustomerMapper;
import com.company.inventory.mapper.ItemMapper;
import com.company.inventory.mapper.StockMapper;
import com.company.inventory.mapper.WarehouseMapper;
import com.company.inventory.model.query.ExpiryAlertQuery;
import com.company.inventory.model.query.LowStockQuery;
import com.company.inventory.service.AlertService;
import com.company.inventory.service.CustomerService;
import com.company.inventory.service.ItemService;
import com.company.inventory.service.SupplierService;
import com.company.inventory.model.vo.alert.ExpiryAlertVO;
import com.company.inventory.model.vo.alert.LowStockVO;
import com.company.inventory.model.vo.customer.CustomerVO;
import com.company.inventory.model.vo.item.ItemVO;
import com.company.inventory.model.vo.supplier.SupplierVO;

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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 主数据与预警测试:供应商/客户 CRUD+编码唯一 + 物品扩展字段 + 临期/低库存预警。
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
class MasterDataAlertTest {

    /** 供应商服务 */
    @Autowired
    private SupplierService supplierService;
    /** 客户服务 */
    @Autowired
    private CustomerService customerService;
    /** 物品服务 */
    @Autowired
    private ItemService itemService;
    /** 预警服务 */
    @Autowired
    private AlertService alertService;
    /** 物品 Mapper */
    @Autowired
    private ItemMapper itemMapper;
    /** 批次 Mapper */
    @Autowired
    private BatchMapper batchMapper;
    /** 库存 Mapper */
    @Autowired
    private StockMapper stockMapper;
    /** 仓库 Mapper */
    @Autowired
    private WarehouseMapper warehouseMapper;
    /** JDBC */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 仓库 */
    private long warehouseId;

    /**
     * 前置:清库并造仓库。
     */
    @BeforeAll
    void cleanDb() {
        String sql = "TRUNCATE \"purchase_order_item\",\"sales_order_item\",\"transfer_doc_item\","
                + "\"stocktake_doc_item\",\"stock_adjust_doc_item\",\"outbound_doc_item\",\"inbound_doc_item\","
                + "\"purchase_order\",\"sales_order\",\"transfer_doc\",\"stocktake_doc\",\"stock_adjust_doc\","
                + "\"outbound_doc\",\"inbound_doc\",\"stock_transaction\",\"stock\",\"serial\",\"batch\","
                + "\"location\",\"item\",\"warehouse\",\"supplier\",\"customer\",\"sys_user\" RESTART IDENTITY CASCADE";
        jdbcTemplate.execute(sql);
        WarehouseDO wh = new WarehouseDO();
        wh.setWarehouseCode("MD-WH");
        wh.setWarehouseName("主数据测试仓");
        wh.setWarehouseType("raw");
        wh.setEnableBatch(true);
        wh.setEnableExpiry(true);
        wh.setEnableSerial(false);
        wh.setEnableLocation(false);
        warehouseMapper.insert(wh);
        warehouseId = wh.getId();
    }

    /**
     * 用例 1:供应商新建 + 查询 + 更新 + 编码唯一。
     */
    @Test
    void supplierCrudAndCodeUnique() {
        SupplierVO sp = supplierService.create(new SupplierCreateDTO("MD-SP-1", "测试供应商",
                null, new BigDecimal("13"), "张三", "13800000000", null, "monthly", null, null), "md_test");
        assertNotNull(sp.id());
        assertEquals("测试供应商", supplierService.get(sp.id()).supplierName());
        SupplierVO updated = supplierService.update(sp.id(), new SupplierUpdateDTO(
                "测试供应商改", null, null, null, null, null, null, null, 1, null), "md_test");
        assertEquals("测试供应商改", updated.supplierName());
        assertThrows(BizException.class, () -> supplierService.create(new SupplierCreateDTO("MD-SP-1", "重复",
                null, null, null, null, null, null, null, null), "md_test"));
        assertTrue(supplierService.list(new com.company.inventory.model.query.SupplierQuery()).rows().size() >= 1);
    }

    /**
     * 用例 2:客户新建 + 编码唯一。
     */
    @Test
    void customerCrudAndCodeUnique() {
        CustomerVO cu = customerService.create(new CustomerCreateDTO("MD-CU-1", "测试客户",
                null, new BigDecimal("13"), null, null, null, null, null, null), "md_test");
        assertNotNull(cu.id());
        assertThrows(BizException.class, () -> customerService.create(new CustomerCreateDTO("MD-CU-1", "重复",
                null, null, null, null, null, null, null, null), "md_test"));
        assertTrue(customerService.list(new com.company.inventory.model.query.CustomerQuery()).rows().size() >= 1);
    }

    /**
     * 用例 3:物品扩展字段(category/minStock/defaultTaxRate)创建与更新。
     */
    @Test
    void itemExtendedFields() {
        ItemVO it = itemService.create(new ItemCreateDTO("MD-IT-1", "扩展物品", "件", "规格A",
                null, "raw", new BigDecimal("5"), new BigDecimal("13")));
        assertEquals("raw", it.category());
        assertEquals(0, new BigDecimal("5").compareTo(it.minStock()));
        ItemVO updated = itemService.update(it.id(), new ItemUpdateDTO("扩展物品改", null, "规格B",
                null, "finished", new BigDecimal("9"), new BigDecimal("9"), 1));
        assertEquals("规格B", updated.spec());
        assertEquals("finished", updated.category());
        assertEquals(0, new BigDecimal("9").compareTo(updated.defaultTaxRate()));
        // 详情不存在 → 404
        assertThrows(BizException.class, () -> itemService.get(-999L));
    }

    /**
     * 用例 4:临期预警——30 天内到期命中,90 天后到期不命中。
     */
    @Test
    void expiryAlertBoundary() {
        long itemId = insertItem("MD-IT-EXP");
        long batchIn = insertBatch(itemId, "B-IN", LocalDate.now().plusDays(10), 5);
        insertStock(itemId, batchIn, new BigDecimal("5"));
        long batchOut = insertBatch(itemId, "B-OUT", LocalDate.now().plusDays(90), 8);
        insertStock(itemId, batchOut, new BigDecimal("8"));

        ExpiryAlertQuery query = new ExpiryAlertQuery();
        PageResult<ExpiryAlertVO> page = alertService.expiry(query);
        assertEquals(1, page.total());
        ExpiryAlertVO row = page.rows().get(0);
        assertEquals("B-IN", row.batchNo());
        assertEquals(10L, row.daysLeft());
    }

    /**
     * 用例 5:低库存预警——全仓可用 < minStock 命中,充足不命中。
     */
    @Test
    void lowStockAlertBoundary() {
        long lowItem = insertItemWithMinStock("MD-IT-LOW", new BigDecimal("20"));
        insertStock(lowItem, 0L, new BigDecimal("5"));
        long okItem = insertItemWithMinStock("MD-IT-OK", new BigDecimal("2"));
        insertStock(okItem, 0L, new BigDecimal("5"));

        LowStockQuery query = new LowStockQuery();
        PageResult<LowStockVO> page = alertService.lowStock(query);
        assertEquals(1, page.total());
        assertEquals("MD-IT-LOW", page.rows().get(0).itemCode());
        // 关键字过滤
        query.setItemKeyword("MD-IT-OK");
        assertEquals(0, alertService.lowStock(query).total());
    }

    /**
     * 造物品。
     *
     * @param code 编码
     * @return 物品 ID
     */
    private long insertItem(String code) {
        return insertItemWithMinStock(code, null);
    }

    /**
     * 造物品(指定最低库存)。
     *
     * @param code     编码
     * @param minStock 最低库存
     * @return 物品 ID
     */
    private long insertItemWithMinStock(String code, BigDecimal minStock) {
        ItemDO item = new ItemDO();
        item.setItemCode(code);
        item.setItemName(code + "名称");
        item.setUnit("件");
        item.setMinStock(minStock);
        itemMapper.insert(item);
        return item.getId();
    }

    /**
     * 造批次。
     *
     * @param itemId 物品 ID
     * @param batchNo 批次号
     * @param expiryDate 到期日
     * @param qty 数量(仅备注)
     * @return 批次 ID
     */
    private long insertBatch(long itemId, String batchNo, LocalDate expiryDate, int qty) {
        BatchDO batch = new BatchDO();
        batch.setItemId(itemId);
        batch.setBatchNo(batchNo);
        batch.setProductionDate(expiryDate.minusDays(100));
        batch.setExpiryDate(expiryDate);
        batch.setStatus("active");
        batchMapper.insert(batch);
        return batch.getId();
    }

    /**
     * 造库存行。
     *
     * @param itemId 物品 ID
     * @param batchId 批次 ID
     * @param qty 数量
     */
    private void insertStock(long itemId, long batchId, BigDecimal qty) {
        StockDO stock = new StockDO();
        stock.setWarehouseId(warehouseId);
        stock.setItemId(itemId);
        stock.setBatchId(batchId);
        stock.setQuantity(qty);
        stock.setUpdatedAt(java.time.LocalDateTime.now());
        stockMapper.insert(stock);
    }
}
