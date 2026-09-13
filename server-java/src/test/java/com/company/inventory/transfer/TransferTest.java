package com.company.inventory.transfer;

import com.company.inventory.common.constant.ErrorCode;
import com.company.inventory.common.exception.BizException;
import com.company.inventory.common.support.DataScope;
import com.company.inventory.model.dto.transfer.TransferActionDTO;
import com.company.inventory.model.dto.transfer.TransferCreateDTO;
import com.company.inventory.model.dto.transfer.TransferLineDTO;
import com.company.inventory.model.dto.stock.StockOpRequest;
import com.company.inventory.model.entity.item.ItemDO;
import com.company.inventory.model.entity.stock.StockDO;
import com.company.inventory.model.entity.stock.StockTransactionDO;
import com.company.inventory.model.entity.warehouse.WarehouseDO;
import com.company.inventory.mapper.ItemMapper;
import com.company.inventory.mapper.StockMapper;
import com.company.inventory.mapper.StockTransactionMapper;
import com.company.inventory.mapper.WarehouseMapper;
import com.company.inventory.service.StockCoreService;
import com.company.inventory.service.TransferService;
import com.company.inventory.model.vo.stock.StockLine;
import com.company.inventory.model.vo.transfer.TransferDocVO;

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

/**
 * 调拨单测试:原子执行(源扣目的加/批次跟随/流水成对)+ 源仓不足整单回滚 + 状态机。
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
class TransferTest {

    /** 调拨单服务 */
    @Autowired
    private TransferService transferService;
    /** 库存核心服务 */
    @Autowired
    private StockCoreService stockCoreService;
    /** 物品 Mapper */
    @Autowired
    private ItemMapper itemMapper;
    /** 仓库 Mapper */
    @Autowired
    private WarehouseMapper warehouseMapper;
    /** 库存 Mapper */
    @Autowired
    private StockMapper stockMapper;
    /** 流水 Mapper */
    @Autowired
    private StockTransactionMapper txMapper;
    /** JDBC */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 物品 */
    private long itemId;
    /** 源仓库 */
    private long fromWh;
    /** 目的仓库 */
    private long toWh;

    /**
     * 前置:清库并造数据(两仓一物,源仓入 50 件)。
     */
    @BeforeAll
    void cleanDb() {
        String sql = "TRUNCATE \"purchase_order_item\",\"sales_order_item\",\"transfer_doc_item\","
                + "\"stocktake_doc_item\",\"stock_adjust_doc_item\",\"outbound_doc_item\",\"inbound_doc_item\","
                + "\"purchase_order\",\"sales_order\",\"transfer_doc\",\"stocktake_doc\",\"stock_adjust_doc\","
                + "\"outbound_doc\",\"inbound_doc\",\"stock_transaction\",\"stock\",\"serial\",\"batch\","
                + "\"location\",\"item\",\"warehouse\",\"supplier\",\"customer\",\"sys_user\" RESTART IDENTITY CASCADE";
        jdbcTemplate.execute(sql);
        fromWh = insertWarehouse("TR-FROM");
        toWh = insertWarehouse("TR-TO");
        ItemDO item = new ItemDO();
        item.setItemCode("TR-IT");
        item.setItemName("调拨测试物");
        item.setUnit("件");
        itemMapper.insert(item);
        itemId = item.getId();
        inbound(fromWh, 50);
    }

    /**
     * 每用例前置:源仓重置 50、目的仓清零(用例间隔离)。
     */
    @BeforeEach
    void resetStock() {
        jdbcTemplate.update("UPDATE \"stock\" SET \"quantity\" = 50 WHERE \"warehouse_id\" = ?", fromWh);
        jdbcTemplate.update("DELETE FROM \"stock\" WHERE \"warehouse_id\" = ?", toWh);
        jdbcTemplate.update("DELETE FROM \"stock_transaction\" WHERE \"warehouse_id\" = ?", toWh);
    }

    /**
     * 用例 1:审批执行 → 源扣目的加,流水 transfer_out/transfer_in 成对。
     */
    @Test
    void approveExecutesTransfer() {
        long id = createDoc("20");
        TransferDocVO vo = transferService.approve(id, "tr_approver");
        assertEquals("completed", vo.status());
        assertEquals(0, new BigDecimal("30").compareTo(stockQty(fromWh)));
        assertEquals(0, new BigDecimal("20").compareTo(stockQty(toWh)));
        assertEquals(1, txCount(fromWh, "transfer_out"));
        assertEquals(1, txCount(toWh, "transfer_in"));
        // 表头合计:20 × 5 = 100
        assertEquals(0, new BigDecimal("100").compareTo(new BigDecimal(vo.totalAmount())));
    }

    /**
     * 用例 2:源仓可用量不足 → 整单失败回滚,库存不变,单据保持可重试(pending)。
     */
    @Test
    void insufficientSourceRollsBack() {
        long id = createDoc("60");
        BizException ex = assertThrows(BizException.class, () -> transferService.approve(id, "tr_approver"));
        assertNotNull(ex);
        assertEquals(0, new BigDecimal("50").compareTo(stockQty(fromWh)));
        assertEquals(0, BigDecimal.ZERO.compareTo(stockQty(toWh)));
        assertEquals(0, txCount(fromWh, "transfer_out"));
        // 回滚后状态 pending,可重试
        assertEquals("pending", transferService.get(id).status());
    }

    /**
     * 用例 3:源仓=目的仓 → 拒绝。
     */
    @Test
    void sameWarehouseRejected() {
        assertThrows(BizException.class, () -> transferService.create(
                new TransferCreateDTO(LocalDate.now(), fromWh, fromWh, null,
                        List.of(new TransferLineDTO(itemId, new BigDecimal("1"),
                                new BigDecimal("1"), null, null, null, null))), "tr_creator"));
    }

    /**
     * 用例 4:状态机——驳回必填原因;驳回后可编辑回 draft;终态只读。
     */
    @Test
    void stateMachine() {
        long id = createDoc("5");
        assertThrows(BizException.class, () ->
                transferService.reject(id, new TransferActionDTO(null), "tr_approver"));
        assertEquals("rejected", transferService
                .reject(id, new TransferActionDTO("路线不通"), "tr_approver").status());
        assertEquals("draft", transferService.update(id,
                new TransferCreateDTO(LocalDate.now(), fromWh, toWh, "改",
                        List.of(new TransferLineDTO(itemId, new BigDecimal("3"),
                                new BigDecimal("2"), null, null, null, null))), "tr_creator").status());
        transferService.submit(id, "tr_creator");
        transferService.approve(id, "tr_approver");
        // completed 终态:不可作废/提交
        assertThrows(BizException.class, () -> transferService.voidDoc(id, "tr_approver"));
        assertThrows(BizException.class, () -> transferService.submit(id, "tr_approver"));
    }

    /**
     * 用例 5:草稿直接审批 → 拒绝;审批人=制单人 → 拒绝;他人审批通过。
     */
    @Test
    void approveGuards() {
        // 草稿(未提交)不可审批
        long draftId = transferService.create(
                new TransferCreateDTO(LocalDate.now(), fromWh, toWh, null,
                        List.of(new TransferLineDTO(itemId, new BigDecimal("5"),
                                new BigDecimal("1"), null, null, null, null))), "tr_creator").id();
        assertThrows(BizException.class, () -> transferService.approve(draftId, "tr_approver"));

        // 已提交:createDoc 内部已提交
        long id = createDoc("5");
        // 审批人=制单人 → 拒绝
        assertThrows(BizException.class, () -> transferService.approve(id, "tr_creator"));
        // 他人审批通过
        assertEquals("completed", transferService.approve(id, "tr_approver").status());
    }

    /**
     * 用例 5:数据权限按单据 ID——非 admin 授权仓不含源/目的仓 → 详情/作废 403;授权目的仓(任一命中)→ 放行。
     */
    @Test
    void dataScopeByDocId() {
        long id = createDoc("5");
        try {
            DataScope.set(List.of(999999L));
            BizException ex = assertThrows(BizException.class, () -> transferService.get(id));
            assertEquals(ErrorCode.FORBIDDEN, ex.getCode(), "无权仓库应 403 而非 200");
            assertEquals(403, ex.getStatus());
            assertThrows(BizException.class, () -> transferService.voidDoc(id, "tr_approver"));
            // 源仓或目的仓任一在授权列表即放行
            DataScope.set(List.of(toWh));
            assertNotNull(transferService.get(id));
        } finally {
            DataScope.clear();
        }
    }

    /**
     * 新建调拨单并返回 ID。
     *
     * @param qty 数量
     * @return 调拨单 ID
     */
    private long createDoc(String qty) {
        TransferDocVO vo = transferService.create(
                new TransferCreateDTO(LocalDate.now(), fromWh, toWh, null,
                        List.of(new TransferLineDTO(itemId, new BigDecimal(qty),
                                new BigDecimal("5"), null, null, null, null))), "tr_creator");
        transferService.submit(vo.id(), "tr_creator");
        return vo.id();
    }

    /**
     * 造仓库。
     *
     * @param code 编码
     * @return 仓库 ID
     */
    private long insertWarehouse(String code) {
        WarehouseDO wh = new WarehouseDO();
        wh.setWarehouseCode(code);
        wh.setWarehouseName(code + "仓");
        wh.setWarehouseType("raw");
        wh.setEnableBatch(false);
        wh.setEnableExpiry(false);
        wh.setEnableSerial(false);
        wh.setEnableLocation(false);
        warehouseMapper.insert(wh);
        return wh.getId();
    }

    /**
     * 手工入库。
     *
     * @param whId 仓库 ID
     * @param qty  数量
     */
    private void inbound(long whId, int qty) {
        StockOpRequest request = new StockOpRequest();
        request.setWarehouseId(whId);
        StockLine line = new StockLine();
        line.setItemId(itemId);
        line.setQty(new BigDecimal(qty));
        request.setLines(List.of(line));
        request.setDocNo("RK-TR");
        request.setOperator("tr_test");
        stockCoreService.inbound(request);
    }

    /**
     * 仓库内该物品总库存。
     *
     * @param whId 仓库 ID
     * @return 总量
     */
    private BigDecimal stockQty(long whId) {
        List<StockDO> rows = stockMapper.selectList(new LambdaQueryWrapper<StockDO>()
                .eq(StockDO::getWarehouseId, whId).eq(StockDO::getItemId, itemId));
        BigDecimal total = BigDecimal.ZERO;
        for (StockDO row : rows) {
            total = total.add(row.getQuantity());
        }
        return total;
    }

    /**
     * 流水计数。
     *
     * @param whId 仓库 ID
     * @param bizCode 业务类型
     * @return 条数
     */
    private long txCount(long whId, String bizCode) {
        Long count = txMapper.selectCount(new LambdaQueryWrapper<StockTransactionDO>()
                .eq(StockTransactionDO::getWarehouseId, whId)
                .eq(StockTransactionDO::getBizCode, bizCode));
        return count == null ? 0 : count;
    }
}
