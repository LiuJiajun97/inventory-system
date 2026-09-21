package com.company.inventory.opening;

import com.company.inventory.common.exception.BizException;
import com.company.inventory.common.page.PageResult;
import com.company.inventory.common.support.DataScope;
import com.company.inventory.model.dto.opening.OpeningStockCreateDTO;
import com.company.inventory.model.dto.opening.OpeningStockLineDTO;
import com.company.inventory.model.entity.item.ItemDO;
import com.company.inventory.model.entity.stock.StockDO;
import com.company.inventory.model.entity.warehouse.WarehouseDO;
import com.company.inventory.mapper.ItemMapper;
import com.company.inventory.mapper.StockMapper;
import com.company.inventory.mapper.WarehouseMapper;
import com.company.inventory.model.query.OpeningStockQuery;
import com.company.inventory.model.vo.opening.OpeningStockCreatedVO;
import com.company.inventory.model.vo.opening.OpeningStockDocVO;
import com.company.inventory.service.OpeningStockService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 期初库存模块测试:正常过账(联动入库单 ref_type=opening)+ 每物品×仓库限一次期初
 * + 批次仓必填批次号 + 序列号仓拒收 + 列表数据权限与分页规范。
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
class OpeningStockTest {

    /** 期初单服务 */
    @Autowired
    private OpeningStockService openingStockService;
    /** 物品 Mapper */
    @Autowired
    private ItemMapper itemMapper;
    /** 仓库 Mapper */
    @Autowired
    private WarehouseMapper warehouseMapper;
    /** 库存 Mapper */
    @Autowired
    private StockMapper stockMapper;
    /** JDBC */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 普通仓库(无批次/保质期/序列号/库位) */
    private long warehouseId;
    /** 批次仓(enableBatch + enableExpiry) */
    private long batchWarehouseId;
    /** 序列号仓 */
    private long serialWarehouseId;
    /** 物品 1(带规格) */
    private long itemId;
    /** 物品 2 */
    private long itemId2;

    /**
     * 前置:清库并造基础主数据(三仓两物)。
     */
    @BeforeAll
    void cleanDb() {
        String sql = "TRUNCATE \"opening_stock_doc_item\",\"opening_stock_doc\","
                + "\"inbound_doc_item\",\"inbound_doc\",\"outbound_doc_item\",\"outbound_doc\","
                + "\"purchase_return_item\",\"sales_return_item\",\"purchase_return\",\"sales_return\","
                + "\"purchase_order_item\",\"sales_order_item\",\"transfer_doc_item\","
                + "\"stocktake_doc_item\",\"stock_adjust_doc_item\",\"purchase_order\",\"sales_order\","
                + "\"transfer_doc\",\"stocktake_doc\",\"stock_adjust_doc\","
                + "\"stock_transaction\",\"stock\",\"serial\",\"batch\","
                + "\"location\",\"item\",\"warehouse\",\"supplier\",\"customer\",\"sys_user\" "
                + "RESTART IDENTITY CASCADE";
        jdbcTemplate.execute(sql);
        warehouseId = insertWarehouse("OP-WH", false, false, false);
        batchWarehouseId = insertWarehouse("OP-BWH", true, true, false);
        serialWarehouseId = insertWarehouse("OP-SWH", false, false, true);
        itemId = insertItem("OP-IT1", "期初测试物 1", "件", "规格 A");
        itemId2 = insertItem("OP-IT2", "期初测试物 2", "个", null);
    }

    /**
     * 每用例前置:清空库存/流水/批次/序列号/期初单/入库单(用例间隔离)。
     */
    @BeforeEach
    void resetStock() {
        jdbcTemplate.execute("TRUNCATE \"opening_stock_doc_item\",\"opening_stock_doc\","
                + "\"inbound_doc_item\",\"inbound_doc\",\"stock\",\"stock_transaction\","
                + "\"serial\",\"batch\"");
    }

    /**
     * 用例 1:期初正常 → 过账,库存 +N,生成入库单 ref_type=opening 且
     * ref_doc_id=期初单 ID,期初单 status=finished,total_qty 正确,单号 QC 前缀。
     */
    @Test
    void openingNormal() {
        OpeningStockCreatedVO vo = openingStockService.create(new OpeningStockCreateDTO(
                warehouseId, LocalDate.now(), "系统启用期初", List.of(
                        new OpeningStockLineDTO(itemId, new BigDecimal("10"),
                                new BigDecimal("5.00"), null, null, null, null),
                        new OpeningStockLineDTO(itemId2, new BigDecimal("2.5"),
                                null, null, null, null, null))), "op_creator");
        assertNotNull(vo.id());
        assertTrue(vo.docNo().startsWith("QC-"), "期初单号应以 QC- 开头: " + vo.docNo());
        assertEquals("finished", vo.status());

        // 期初单头:total_qty = 10 + 2.5 = 12.5
        BigDecimal totalQty = jdbcTemplate.queryForObject(
                "SELECT total_qty FROM opening_stock_doc WHERE id = ?", BigDecimal.class, vo.id());
        assertEquals(0, new BigDecimal("12.5").compareTo(totalQty));
        // 期初行:规格/单位快照 + 行号连号
        Long lineCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM opening_stock_doc_item WHERE doc_id = ?", Long.class, vo.id());
        assertEquals(2L, lineCount);
        String spec = jdbcTemplate.queryForObject(
                "SELECT spec_snapshot FROM opening_stock_doc_item WHERE doc_id = ? AND line_no = 1",
                String.class, vo.id());
        assertEquals("规格 A", spec);

        // 库存:两物品分别 +10 / +2.5
        assertEquals(0, new BigDecimal("10").compareTo(stockQty(warehouseId, itemId)));
        assertEquals(0, new BigDecimal("2.5").compareTo(stockQty(warehouseId, itemId2)));
        // 联动入库单:ref_type=opening,ref_doc_id=期初单 ID,status=finished
        Long inCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM inbound_doc WHERE ref_type = 'opening'"
                        + " AND ref_doc_id = ? AND status = 'finished'", Long.class, vo.id());
        assertEquals(1L, inCount);
        // 流水:入库 2 笔且单据号为期初联动入库单号
        Long txCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM stock_transaction WHERE biz_code = 'inbound'"
                        + " AND doc_no = (SELECT doc_no FROM inbound_doc"
                        + " WHERE ref_type = 'opening' AND ref_doc_id = ?)",
                Long.class, vo.id());
        assertEquals(2L, txCount);
    }

    /**
     * 用例 2:同物品同仓再次期初 → 拒绝(该行已有期初),整单回滚,已有库存不变。
     */
    @Test
    void openingOncePerItemWarehouse() {
        openingStockService.create(new OpeningStockCreateDTO(
                warehouseId, LocalDate.now(), null, List.of(
                        new OpeningStockLineDTO(itemId, new BigDecimal("5"),
                                null, null, null, null, null))), "op_creator");
        assertEquals(0, new BigDecimal("5").compareTo(stockQty(warehouseId, itemId)));

        // 第二次同物品同仓(混入新物品也整单拒)→ 整单回滚
        BizException ex = assertThrows(BizException.class, () -> openingStockService.create(
                new OpeningStockCreateDTO(
                        warehouseId, LocalDate.now(), null, List.of(
                                new OpeningStockLineDTO(itemId2, new BigDecimal("3"),
                                        null, null, null, null, null),
                                new OpeningStockLineDTO(itemId, new BigDecimal("3"),
                                        null, null, null, null, null))), "op_creator"));
        assertTrue(ex.getMessage().contains("已有期初"), "提示应说明已有期初: " + ex.getMessage());
        // 整单回滚:库存与期初单数量不变,新品物 itemId2 也未入账
        assertEquals(0, new BigDecimal("5").compareTo(stockQty(warehouseId, itemId)));
        assertEquals(0, BigDecimal.ZERO.compareTo(stockQty(warehouseId, itemId2)));
        Long docCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM opening_stock_doc", Long.class);
        assertEquals(1L, docCount);
        // 同物品换仓不受限(不同仓库可期初)
        OpeningStockCreatedVO other = openingStockService.create(new OpeningStockCreateDTO(
                batchWarehouseId, LocalDate.now(), null, List.of(
                        new OpeningStockLineDTO(itemId, new BigDecimal("1"),
                                null, "OP-B1", null, null, null))), "op_creator");
        assertNotNull(other.id());
    }

    /**
     * 用例 3:批次仓(enableBatch/enableExpiry)期初不带批次号 → 拒绝,库存零变化。
     */
    @Test
    void batchWarehouseMissingBatchNoRejected() {
        BizException ex = assertThrows(BizException.class, () -> openingStockService.create(
                new OpeningStockCreateDTO(
                        batchWarehouseId, LocalDate.now(), null, List.of(
                                new OpeningStockLineDTO(itemId, new BigDecimal("2"),
                                        null, null, null, null, null))), "op_creator"));
        assertTrue(ex.getMessage().contains("批次"), "提示应说明批次必填: " + ex.getMessage());
        assertEquals(0, BigDecimal.ZERO.compareTo(stockQty(batchWarehouseId, itemId)));
        Long docCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM opening_stock_doc", Long.class);
        assertEquals(0L, docCount);
    }

    /**
     * 用例 4:序列号仓期初 → 拒"期初不支持序列号物品,请走入库单",库存零变化。
     */
    @Test
    void serialWarehouseRejected() {
        BizException ex = assertThrows(BizException.class, () -> openingStockService.create(
                new OpeningStockCreateDTO(
                        serialWarehouseId, LocalDate.now(), null, List.of(
                                new OpeningStockLineDTO(itemId, new BigDecimal("2"),
                                        null, null, null, null, null))), "op_creator"));
        assertTrue(ex.getMessage().contains("期初不支持序列号"),
                "提示应说明期初不支持序列号: " + ex.getMessage());
        assertEquals(0, BigDecimal.ZERO.compareTo(stockQty(serialWarehouseId, itemId)));
    }

    /**
     * 用例 5:列表数据权限(未授权查空/授权仓过滤/admin 豁免)+ 分页规范
     * {rows,total,page,pageSize} + 详情头行完整。
     */
    @Test
    void listDataScopeAndPage() {
        OpeningStockCreatedVO vo = openingStockService.create(new OpeningStockCreateDTO(
                warehouseId, LocalDate.now(), "列表用例", List.of(
                        new OpeningStockLineDTO(itemId, new BigDecimal("4"),
                                new BigDecimal("1.50"), null, null, null, null),
                        new OpeningStockLineDTO(itemId2, new BigDecimal("6"),
                                null, null, null, null, null))), "op_creator");

        OpeningStockQuery query = new OpeningStockQuery();
        // 未授权任何仓库 → 查空,分页规范 {rows,total,page,pageSize}
        try {
            DataScope.set(List.of(999999L));
            PageResult<OpeningStockDocVO> none = openingStockService.list(query);
            assertEquals(0L, none.total());
            assertTrue(none.rows().isEmpty());
            assertEquals(1L, none.page());
            assertEquals(20L, none.pageSize());
        } finally {
            DataScope.clear();
        }
        // 授权本仓 → 可见且全部属于本仓
        try {
            DataScope.set(List.of(warehouseId));
            PageResult<OpeningStockDocVO> scoped = openingStockService.list(query);
            assertTrue(scoped.total() >= 1);
            for (OpeningStockDocVO row : scoped.rows()) {
                assertEquals(warehouseId, row.warehouseId());
            }
        } finally {
            DataScope.clear();
        }
        // admin 豁免(未设置 DataScope):全可见,行摘要完整
        PageResult<OpeningStockDocVO> all = openingStockService.list(query);
        assertTrue(all.total() >= 1);
        assertEquals(query.getPage(), all.page());
        assertEquals(query.getPageSize(), all.pageSize());
        // 详情:头 + 行完整
        OpeningStockDocVO doc = openingStockService.get(vo.id());
        assertEquals(vo.docNo(), doc.docNo());
        assertEquals(2, doc.items().size());
        assertEquals(0, new BigDecimal("10").compareTo(new BigDecimal(doc.totalQty())));
        assertNotNull(doc.warehouse());
        assertEquals(itemId, doc.items().get(0).itemId());
    }

    /**
     * 仓库内指定物品总库存。
     *
     * @param whId 仓库 ID
     * @param itId 物品 ID
     * @return 总量
     */
    private BigDecimal stockQty(long whId, long itId) {
        List<StockDO> rows = stockMapper.selectList(new LambdaQueryWrapper<StockDO>()
                .eq(StockDO::getWarehouseId, whId).eq(StockDO::getItemId, itId));
        BigDecimal total = BigDecimal.ZERO;
        for (StockDO row : rows) {
            total = total.add(row.getQuantity());
        }
        return total;
    }

    /**
     * 造仓库。
     *
     * @param code      编码
     * @param batch     启用批次
     * @param expiry    启用保质期
     * @param serial    启用序列号
     * @return 仓库 ID
     */
    private long insertWarehouse(String code, boolean batch, boolean expiry, boolean serial) {
        WarehouseDO wh = new WarehouseDO();
        wh.setWarehouseCode(code);
        wh.setWarehouseName(code + "仓");
        wh.setWarehouseType("raw");
        wh.setEnableBatch(batch);
        wh.setEnableExpiry(expiry);
        wh.setEnableSerial(serial);
        wh.setEnableLocation(false);
        warehouseMapper.insert(wh);
        return wh.getId();
    }

    /**
     * 造物品。
     *
     * @param code 编码
     * @param name 名称
     * @param unit 单位
     * @param spec 规格(可空)
     * @return 物品 ID
     */
    private long insertItem(String code, String name, String unit, String spec) {
        ItemDO item = new ItemDO();
        item.setItemCode(code);
        item.setItemName(name);
        item.setUnit(unit);
        item.setSpec(spec);
        itemMapper.insert(item);
        return item.getId();
    }
}
