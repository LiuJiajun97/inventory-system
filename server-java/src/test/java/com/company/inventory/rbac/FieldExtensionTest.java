package com.company.inventory.rbac;

import com.company.inventory.common.exception.BizException;
import com.company.inventory.model.dto.inbound.InboundCreateDTO;
import com.company.inventory.model.dto.inbound.InboundLineDTO;
import com.company.inventory.model.dto.item.ItemCreateDTO;
import com.company.inventory.model.dto.item.ItemUpdateDTO;
import com.company.inventory.model.dto.outbound.OutboundCreateDTO;
import com.company.inventory.model.dto.outbound.OutboundLineDTO;
import com.company.inventory.model.dto.stocktake.StocktakeActualDTO;
import com.company.inventory.model.dto.stocktake.StocktakeActualLineDTO;
import com.company.inventory.model.dto.stocktake.StocktakeCreateDTO;
import com.company.inventory.model.dto.supplier.SupplierCreateDTO;
import com.company.inventory.model.dto.supplier.SupplierUpdateDTO;
import com.company.inventory.model.entity.item.ItemDO;
import com.company.inventory.model.entity.location.LocationDO;
import com.company.inventory.model.entity.stock.SerialDO;
import com.company.inventory.model.entity.warehouse.WarehouseDO;
import com.company.inventory.mapper.ItemMapper;
import com.company.inventory.mapper.LocationMapper;
import com.company.inventory.mapper.SerialMapper;
import com.company.inventory.mapper.WarehouseMapper;
import com.company.inventory.service.InboundService;
import com.company.inventory.service.ItemService;
import com.company.inventory.service.OutboundService;
import com.company.inventory.service.StocktakeService;
import com.company.inventory.service.SupplierService;
import com.company.inventory.model.vo.inbound.InboundDocCreatedVO;
import com.company.inventory.model.vo.inbound.InboundDocVO;
import com.company.inventory.model.vo.item.ItemVO;
import com.company.inventory.model.vo.outbound.OutboundDocCreatedVO;
import com.company.inventory.model.vo.outbound.OutboundDocVO;
import com.company.inventory.model.vo.stocktake.StocktakeDocVO;
import com.company.inventory.model.vo.supplier.SupplierVO;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * V9 通用字段补全测试:主数据/单据头/调拨行/盘点行新字段回读 + 条码唯一 + 序列号追溯链。
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
class FieldExtensionTest {

    /** 供应商服务 */
    @Autowired
    private SupplierService supplierService;
    /** 物品服务 */
    @Autowired
    private ItemService itemService;
    /** 入库服务 */
    @Autowired
    private InboundService inboundService;
    /** 出库服务 */
    @Autowired
    private OutboundService outboundService;
    /** 盘点服务 */
    @Autowired
    private StocktakeService stocktakeService;
    /** 仓库 Mapper */
    @Autowired
    private WarehouseMapper warehouseMapper;
    /** 库位 Mapper */
    @Autowired
    private LocationMapper locationMapper;
    /** 物品 Mapper */
    @Autowired
    private ItemMapper itemMapper;
    /** 序列号 Mapper */
    @Autowired
    private SerialMapper serialMapper;
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
     * 用例 1:供应商 V9 新字段(开户行/银行账号/信用额度/交货地址)创建与回读。
     */
    @Test
    void supplierBankFields() {
        SupplierVO sp = supplierService.create(new SupplierCreateDTO("V9-SP-1", "V9 供应商",
                null, null, null, null, null, null, null,
                "工商银行", "6222020200112233445", new BigDecimal("10000.00"), "送货路 1 号", null), "v9_test");
        assertNotNull(sp.id());
        SupplierVO got = supplierService.get(sp.id());
        assertEquals("工商银行", got.bankName());
        assertEquals("6222020200112233445", got.bankAccount());
        assertEquals(0, new BigDecimal("10000.00").compareTo(got.creditLimit()));
        assertEquals("送货路 1 号", got.deliveryAddress());
        // 编辑:非空覆盖语义
        SupplierVO upd = supplierService.update(sp.id(), new SupplierUpdateDTO(null, null, null,
                null, null, null, null, null, "中国银行", "6217889900112233",
                new BigDecimal("20000"), "送货路 2 号", 1, null), "v9_test");
        assertEquals("中国银行", upd.bankName());
        assertEquals("6217889900112233", upd.bankAccount());
        assertEquals(0, new BigDecimal("20000").compareTo(upd.creditLimit()));
        assertEquals("送货路 2 号", upd.deliveryAddress());
    }

    /**
     * 用例 2:物品 V9 新字段(条码/辅助单位/换算率/品牌)回读 + 条码唯一 400。
     */
    @Test
    void itemBarcodeAndDualUnit() {
        ItemVO it = itemService.create(new ItemCreateDTO("V9-IT-1", "V9 物品", "件", null, null,
                null, null, null, "69012345678901", "箱", new BigDecimal("24"), "测试品牌"));
        assertNotNull(it.id());
        ItemVO got = itemService.get(it.id());
        assertEquals("69012345678901", got.barcode());
        assertEquals("箱", got.secondUnit());
        assertEquals(0, new BigDecimal("24").compareTo(got.convertFactor()));
        assertEquals("测试品牌", got.brand());
        // 同条码第二物品 → 400
        BizException dupCreate = assertThrows(BizException.class,
                () -> itemService.create(new ItemCreateDTO("V9-IT-2", "V9 物品 2", "件", null, null,
                        null, null, null, "69012345678901", null, null, null)));
        assertEquals(400, dupCreate.getStatus());
        // 更新为他人已占用条码 → 400(保留自身条码不冲突)
        itemService.create(new ItemCreateDTO("V9-IT-2", "V9 物品 2", "件", null, null,
                null, null, null, "69099999999999", null, null, null));
        BizException dupUpdate = assertThrows(BizException.class,
                () -> itemService.update(it.id(), new ItemUpdateDTO(null, null, null, null, null,
                        null, null, "69099999999999", null, null, null, 1)));
        assertEquals(400, dupUpdate.getStatus());
    }

    /**
     * 用例 3:入库/出库单头运输字段回读 + 序列号追溯链(ref_doc_no / ref_out_doc_no)。
     */
    @Test
    void inboundOutboundTransportAndSerialTrace() {
        long[] env = setupSerialWarehouse("V9-WH-1");
        long whId = env[0];
        long locId = env[1];
        ItemDO item = insertItem("V9-IT-3");

        // 入库 1 件带序列号
        InboundLineDTO line = new InboundLineDTO(item.getId(), BigDecimal.ONE, null, null, null,
                "供应商A", locId, List.of("SN-V9-001"), new BigDecimal("10"), new BigDecimal("13"), null);
        InboundDocCreatedVO in = inboundService.create(new InboundCreateDTO(whId, "V9 入库",
                List.of(line), null, null, LocalDate.now(), "顺丰速运", "沪A12345",
                new BigDecimal("88.50")), "v9_test");
        InboundDocVO inVo = inboundService.get(in.id());
        assertEquals("顺丰速运", inVo.carrier());
        assertEquals("沪A12345", inVo.vehicleNo());
        assertEquals("88.5", inVo.freight());
        // 追溯链:入库单号回写 serial.ref_doc_no
        SerialDO sn = serialMapper.selectOne(new LambdaQueryWrapper<SerialDO>()
                .eq(SerialDO::getSerialNo, "SN-V9-001"));
        assertNotNull(sn);
        assertEquals(in.docNo(), sn.getRefDocNo());
        assertNull(sn.getRefOutDocNo());

        // 出库 1 件同序列号
        OutboundLineDTO outLine = new OutboundLineDTO(item.getId(), BigDecimal.ONE, null, locId,
                List.of("SN-V9-001"), new BigDecimal("10"), null);
        OutboundDocCreatedVO out = outboundService.create(new OutboundCreateDTO(whId, "V9 出库",
                List.of(outLine), null, null, LocalDate.now(), "德邦物流", "沪B67890",
                new BigDecimal("66.00")), "v9_test");
        OutboundDocVO outVo = outboundService.get(out.id());
        assertEquals("德邦物流", outVo.carrier());
        assertEquals("沪B67890", outVo.vehicleNo());
        assertEquals("66", outVo.freight());
        // 追溯链:出库单号回写 serial.ref_out_doc_no,入库追溯保留
        SerialDO sn2 = serialMapper.selectOne(new LambdaQueryWrapper<SerialDO>()
                .eq(SerialDO::getSerialNo, "SN-V9-001"));
        assertEquals(out.docNo(), sn2.getRefOutDocNo());
        assertEquals(in.docNo(), sn2.getRefDocNo());
    }

    /**
     * 用例 4:盘点实盘录入带盘点人/盘点日期,详情回读一致。
     */
    @Test
    void stocktakeCheckerFields() {
        long[] env = setupPlainWarehouse("V9-WH-2");
        long whId = env[0];
        ItemDO item = insertItem("V9-IT-4");
        InboundLineDTO line = new InboundLineDTO(item.getId(), BigDecimal.valueOf(3), null, null,
                null, null, null, null, new BigDecimal("5"), new BigDecimal("13"), null);
        inboundService.create(new InboundCreateDTO(whId, "V9 盘点备货", List.of(line),
                null, null, LocalDate.now(), null, null, null), "v9_test");

        StocktakeDocVO doc = stocktakeService.create(new StocktakeCreateDTO(whId, LocalDate.now(),
                "all", null, "V9 盘点"), "v9_test");
        long lineId = doc.items().get(0).id();
        StocktakeDocVO after = stocktakeService.enterActual(doc.id(),
                new StocktakeActualDTO(List.of(new StocktakeActualLineDTO(lineId,
                        new BigDecimal("2"), "盘点员小王", LocalDate.now()))), "v9_test");
        assertEquals("盘点员小王", after.items().get(0).checkerName());
        assertEquals(LocalDate.now(), after.items().get(0).checkDate());
        assertEquals("2", after.items().get(0).actualQty());
    }

    /**
     * 造启用序列号的仓库(批次关/保质期关/序列号开/库位开)+ 库位。
     *
     * @param code 仓库编码(唯一)
     * @return [warehouseId, locationId]
     */
    private long[] setupSerialWarehouse(String code) {
        WarehouseDO wh = new WarehouseDO();
        wh.setWarehouseCode(code);
        wh.setWarehouseName(code + " 仓");
        wh.setWarehouseType("raw");
        wh.setEnableBatch(false);
        wh.setEnableExpiry(false);
        wh.setEnableSerial(true);
        wh.setEnableLocation(true);
        warehouseMapper.insert(wh);
        LocationDO loc = new LocationDO();
        loc.setWarehouseId(wh.getId());
        loc.setLocationCode(code + "-L1");
        loc.setLocationName("默认位");
        locationMapper.insert(loc);
        return new long[]{wh.getId(), loc.getId()};
    }

    /**
     * 造普通仓库(全开关关闭)。
     *
     * @param code 仓库编码(唯一)
     * @return [warehouseId, 0]
     */
    private long[] setupPlainWarehouse(String code) {
        WarehouseDO wh = new WarehouseDO();
        wh.setWarehouseCode(code);
        wh.setWarehouseName(code + " 仓");
        wh.setWarehouseType("raw");
        wh.setEnableBatch(false);
        wh.setEnableExpiry(false);
        wh.setEnableSerial(false);
        wh.setEnableLocation(false);
        warehouseMapper.insert(wh);
        return new long[]{wh.getId(), 0L};
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
}
