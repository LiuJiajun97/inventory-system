package com.company.inventory.adjust;

import com.company.inventory.common.exception.BizException;
import com.company.inventory.model.dto.adjust.StockAdjustCreateDTO;
import com.company.inventory.model.dto.adjust.StockAdjustLineDTO;
import com.company.inventory.model.dto.stock.StockOpRequest;
import com.company.inventory.model.entity.item.ItemDO;
import com.company.inventory.model.entity.warehouse.WarehouseDO;
import com.company.inventory.mapper.ItemMapper;
import com.company.inventory.mapper.WarehouseMapper;
import com.company.inventory.service.StockAdjustService;
import com.company.inventory.service.StockCoreService;
import com.company.inventory.model.vo.adjust.StockAdjustDocItemVO;
import com.company.inventory.model.vo.adjust.StockAdjustDocVO;
import com.company.inventory.model.vo.stock.StockLine;

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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 库存调整单编辑测试:草稿/已驳回可编辑(行全量替换、已驳回回 draft 清驳回原因),
 * 待审批/已审批/已完成/已作废不可编辑,不存在 id 抛异常。
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
class StockAdjustServiceTest {

    /** 调整服务 */
    @Autowired
    private StockAdjustService stockAdjustService;
    /** 库存核心服务 */
    @Autowired
    private StockCoreService stockCoreService;
    /** 物品 Mapper */
    @Autowired
    private ItemMapper itemMapper;
    /** 仓库 Mapper */
    @Autowired
    private WarehouseMapper warehouseMapper;
    /** JDBC */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 物品 A */
    private long itemA;
    /** 物品 B */
    private long itemB;
    /** 仓库 */
    private long warehouseId;

    /**
     * 前置:清库,造一仓两物,各入 10 件。
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
        wh.setWarehouseCode("ADJ-WH");
        wh.setWarehouseName("调整测试仓");
        wh.setWarehouseType("raw");
        wh.setEnableBatch(false);
        wh.setEnableExpiry(false);
        wh.setEnableSerial(false);
        wh.setEnableLocation(false);
        warehouseMapper.insert(wh);
        warehouseId = wh.getId();
        itemA = insertItem("ADJ-A");
        itemB = insertItem("ADJ-B");
        inbound(itemA, 10);
        inbound(itemB, 10);
    }

    /**
     * 用例 1:草稿单 update 成功——行全量替换(2 行改 1 行,数量/类型/备注生效),状态仍 draft。
     */
    @Test
    void updateDraftSucceeds() {
        long id = stockAdjustService.create(
                createDto("gain", "原备注", List.of(new StockAdjustLineDTO(itemA, new BigDecimal("2"),
                        new BigDecimal("1"), null, null, "原行1"),
                        new StockAdjustLineDTO(itemB, new BigDecimal("3"),
                        new BigDecimal("1"), null, null, "原行2"))),
                "adj_creator").id();

        StockAdjustDocVO vo = stockAdjustService.update(id,
                createDto("loss", "改后备注", List.of(new StockAdjustLineDTO(itemB, new BigDecimal("5"),
                        new BigDecimal("2"), null, null, "改后行"))),
                "adj_editor");

        assertEquals("draft", vo.status());
        assertEquals("loss", vo.adjustType());
        assertEquals("改后备注", vo.remark());
        assertEquals("adj_editor", vo.updater());
        assertEquals(1, vo.items().size());
        StockAdjustDocItemVO item = vo.items().get(0);
        assertTrue(item.itemId().equals(itemB));
        assertEquals(Integer.valueOf(1), item.lineNo());
        assertEquals(0, new BigDecimal("5").compareTo(new BigDecimal(item.qty())));
        assertEquals(0, new BigDecimal("2").compareTo(item.unitPrice()));
        assertEquals("改后行", item.reason());
        // 旧行(物品 A)已全量替换掉
        assertTrue(vo.items().stream().noneMatch(l -> l.itemId().equals(itemA)));
    }

    /**
     * 用例 2:已驳回单 update 成功——回 draft 并清空驳回原因。
     */
    @Test
    void updateRejectedBackToDraft() {
        long id = stockAdjustService.create(
                createDto("gain", null, linesOf(itemA, "2")), "adj_creator").id();
        stockAdjustService.submit(id, "adj_creator");
        assertEquals("rejected", stockAdjustService.reject(id, "数量存疑", "adj_approver").status());

        StockAdjustDocVO vo = stockAdjustService.update(id,
                createDto("gain", null, linesOf(itemA, "4")), "adj_editor");
        assertEquals("draft", vo.status());
        assertNull(vo.rejectReason());
        assertEquals("adj_editor", vo.updater());
        assertEquals(1, vo.items().size());
        assertEquals(0, new BigDecimal("4").compareTo(new BigDecimal(vo.items().get(0).qty())));
    }

    /**
     * 用例 3:待审批单 update 拒绝。
     */
    @Test
    void updatePendingRejected() {
        long id = stockAdjustService.create(
                createDto("gain", null, linesOf(itemA, "2")), "adj_creator").id();
        stockAdjustService.submit(id, "adj_creator");
        assertThrows(BizException.class,
                () -> stockAdjustService.update(id, createDto("gain", null, linesOf(itemA, "1")), "adj_editor"));
    }

    /**
     * 用例 4:已审批单 update 拒绝(模拟审批中状态)。
     */
    @Test
    void updateApprovedRejected() {
        long id = stockAdjustService.create(
                createDto("gain", null, linesOf(itemA, "2")), "adj_creator").id();
        stockAdjustService.submit(id, "adj_creator");
        jdbcTemplate.update("UPDATE \"stock_adjust_doc\" SET \"status\" = 'approved' WHERE \"id\" = ?", id);
        assertThrows(BizException.class,
                () -> stockAdjustService.update(id, createDto("gain", null, linesOf(itemA, "1")), "adj_editor"));
    }

    /**
     * 用例 5:已完成单 update 拒绝。
     */
    @Test
    void updateCompletedRejected() {
        long id = stockAdjustService.create(
                createDto("gain", null, linesOf(itemA, "2")), "adj_creator").id();
        stockAdjustService.submit(id, "adj_creator");
        stockAdjustService.approve(id, "adj_approver");
        assertThrows(BizException.class,
                () -> stockAdjustService.update(id, createDto("gain", null, linesOf(itemA, "1")), "adj_editor"));
    }

    /**
     * 用例 6:已作废单 update 拒绝。
     */
    @Test
    void updateVoidedRejected() {
        long id = stockAdjustService.create(
                createDto("gain", null, linesOf(itemA, "2")), "adj_creator").id();
        stockAdjustService.voidDoc(id, "adj_creator");
        assertThrows(BizException.class,
                () -> stockAdjustService.update(id, createDto("gain", null, linesOf(itemA, "1")), "adj_editor"));
    }

    /**
     * 用例 7:不存在的 id update 拒绝。
     */
    @Test
    void updateNonExistentRejected() {
        assertThrows(BizException.class,
                () -> stockAdjustService.update(999_999_999L,
                        createDto("gain", null, linesOf(itemA, "1")), "adj_editor"));
    }

    /**
     * 造调整单入参。
     *
     * @param adjustType 调整类型
     * @param remark     备注(可空)
     * @param lines      调整行
     * @return 入参
     */
    private StockAdjustCreateDTO createDto(String adjustType, String remark, List<StockAdjustLineDTO> lines) {
        return new StockAdjustCreateDTO(warehouseId, LocalDate.now(), adjustType, null, remark, lines);
    }

    /**
     * 造单行入参列表。
     *
     * @param itemId 物品 ID
     * @param qty    数量
     * @return 行列表
     */
    private List<StockAdjustLineDTO> linesOf(long itemId, String qty) {
        return List.of(new StockAdjustLineDTO(itemId, new BigDecimal(qty), new BigDecimal("1"),
                null, null, "默认原因"));
    }

    /**
     * 造物品。
     *
     * @param code 编码
     * @return 物品 ID
     */
    private long insertItem(String code) {
        ItemDO item = new ItemDO();
        item.setItemCode(code);
        item.setItemName(code + "名称");
        item.setUnit("件");
        itemMapper.insert(item);
        return item.getId();
    }

    /**
     * 手工入库。
     *
     * @param itemId 物品 ID
     * @param qty    数量
     */
    private void inbound(long itemId, int qty) {
        StockOpRequest request = new StockOpRequest();
        request.setWarehouseId(warehouseId);
        StockLine line = new StockLine();
        line.setItemId(itemId);
        line.setQty(new BigDecimal(qty));
        request.setLines(List.of(line));
        request.setDocNo("RK-ADJ");
        request.setOperator("adj_test");
        stockCoreService.inbound(request);
    }
}
