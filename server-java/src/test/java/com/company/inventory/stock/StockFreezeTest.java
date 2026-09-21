package com.company.inventory.stock;

import com.company.inventory.common.exception.BizException;
import com.company.inventory.common.support.AuthCache;
import com.company.inventory.support.RbacSeedSupport;
import com.company.inventory.model.dto.stock.StockOpRequest;
import com.company.inventory.model.entity.item.ItemDO;
import com.company.inventory.model.entity.stock.StockDO;
import com.company.inventory.model.entity.warehouse.WarehouseDO;
import com.company.inventory.model.vo.stock.StockLine;
import com.company.inventory.mapper.ItemMapper;
import com.company.inventory.mapper.StockMapper;
import com.company.inventory.mapper.WarehouseMapper;
import com.company.inventory.service.StockCoreService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 库存冻结测试(V26,真实 HTTP,RbacSeedSupport)。
 *
 * <p>覆盖:冻结后手动指定批次出库 400(带原因)、FEFO 自动选批跳过冻结批次、
 * 预占跳过冻结批次、解冻恢复、重复冻结/未冻结解冻 400、冻结不拦入库、
 * 冻结记录 list(freeze+unfreeze 两条)、viewer 冻结 403。</p>
 *
 * @author inventory
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5433/inventory_test",
        "spring.datasource.username=inv",
        "spring.datasource.password=inv123"
})
class StockFreezeTest {

    /** 库存核心服务(入库造数/预占拦截断言)。 */
    @Autowired
    private StockCoreService stockCoreService;
    /** 仓库 Mapper。 */
    @Autowired
    private WarehouseMapper warehouseMapper;
    /** 物品 Mapper。 */
    @Autowired
    private ItemMapper itemMapper;
    /** 库存 Mapper。 */
    @Autowired
    private StockMapper stockMapper;
    /** JDBC(清库存)。 */
    @Autowired
    private JdbcTemplate jdbcTemplate;
    /** 权限缓存(测试前清缓存防串号)。 */
    @Autowired
    private AuthCache authCache;
    /** REST 模板(真实 HTTP 断言)。 */
    @Autowired
    private TestRestTemplate rest;

    /** 冻结测试仓(启用保质期,FEFO 生效)。 */
    private long warehouseId;
    /** 冻结测试物品。 */
    private long itemId;
    /** 预占拦截测试物品。 */
    private long itemId2;

    /**
     * 前置:RBAC 基线 + 自建仓/物品(seed 提供 admin/zhangsan/lisi 三个 HTTP 用户)。
     */
    @BeforeAll
    void cleanDb() {
        RbacSeedSupport.injectBaseline(jdbcTemplate);
        RbacSeedSupport.evictAuthCache(authCache);

        WarehouseDO wh = new WarehouseDO();
        wh.setWarehouseCode("FRZ-WH");
        wh.setWarehouseName("冻结测试仓");
        wh.setWarehouseType("raw");
        wh.setEnableBatch(true);
        wh.setEnableExpiry(true);
        wh.setEnableSerial(false);
        wh.setEnableLocation(false);
        warehouseMapper.insert(wh);
        warehouseId = wh.getId();

        ItemDO item = new ItemDO();
        item.setItemCode("FRZ-IT");
        item.setItemName("冻结测试物");
        item.setUnit("件");
        itemMapper.insert(item);
        itemId = item.getId();

        ItemDO item2 = new ItemDO();
        item2.setItemCode("FRZ-IT2");
        item2.setItemName("预占拦截测试物");
        item2.setUnit("件");
        itemMapper.insert(item2);
        itemId2 = item2.getId();
    }

    /**
     * 每用例前置:清库存/流水/批次并重新造两批次(B1 早到期 10 件 + B2 晚到期 5 件)。
     */
    @BeforeEach
    void resetStock() {
        jdbcTemplate.execute("TRUNCATE \"stock\", \"stock_transaction\", \"batch\", "
                + "\"stock_freeze_log\" RESTART IDENTITY");
        inbound(itemId, "FRZ-B1", "10", 30);
        inbound(itemId, "FRZ-B2", "5", 60);
    }

    /**
     * 用例 1:冻结 → 手动指定该批次出库 400,消息带"已冻结"与冻结原因。
     */
    @Test
    void freezeThenManualOutboundRejected() {
        String admin = httpLogin("admin", "admin123");
        assertFreeze(admin, "FRZ-B1", "质检扣留");

        ResponseEntity<Map> res = outbound(admin, itemId, "3", "FRZ-B1");
        assertEquals(400, res.getStatusCode().value());
        String message = messageOf(res);
        assertTrue(message.contains("已冻结"), "应提示批次已冻结: " + message);
        assertTrue(message.contains("质检扣留"), "应带冻结原因: " + message);
    }

    /**
     * 用例 2:FEFO 自动选批跳过冻结批次(冻早批次 B1 → 选出晚批次 B2)。
     */
    @Test
    void fefoSkipsFrozenBatch() {
        String admin = httpLogin("admin", "admin123");
        assertFreeze(admin, "FRZ-B1", "客诉扣留");

        ResponseEntity<Map> res = outbound(admin, itemId, "5", null);
        assertEquals(200, res.getStatusCode().value());
        StockDO b1 = stockByBatch("FRZ-B1");
        StockDO b2 = stockByBatch("FRZ-B2");
        assertNotNull(b1);
        assertNotNull(b2);
        assertEquals(0, new BigDecimal("10").compareTo(b1.getQuantity()));
        assertEquals(Boolean.TRUE, b1.getFrozen());
        assertEquals(0, BigDecimal.ZERO.compareTo(b2.getQuantity()));
    }

    /**
     * 用例 3:预占跳过冻结批次(冻唯一批次 → 预占 400 库存不足)。
     */
    @Test
    void preAllocSkipsFrozenBatch() {
        String admin = httpLogin("admin", "admin123");
        jdbcTemplate.execute("TRUNCATE \"stock\", \"stock_transaction\", \"batch\" RESTART IDENTITY");
        inbound(itemId2, "FRZ-BX", "8", 90);
        assertFreeze(admin, "FRZ-BX", "破损扣留");

        BizException ex = assertThrows(BizException.class,
                () -> stockCoreService.preAlloc(warehouseId, itemId2, new BigDecimal("8"), "行1"));
        assertTrue(ex.getMessage().contains("库存不足"), "预占应报库存不足: " + ex.getMessage());
    }

    /**
     * 用例 4:解冻后出库恢复放行。
     */
    @Test
    void unfreezeRestoresOutbound() {
        String admin = httpLogin("admin", "admin123");
        assertFreeze(admin, "FRZ-B1", "质检扣留");
        assertEquals(400, outbound(admin, itemId, "3", "FRZ-B1").getStatusCode().value());

        assertEquals(200, unfreeze(admin, "FRZ-B1").getStatusCode().value());
        assertEquals(200, outbound(admin, itemId, "3", "FRZ-B1").getStatusCode().value());
        assertEquals(0, new BigDecimal("7").compareTo(stockByBatch("FRZ-B1").getQuantity()));
    }

    /**
     * 用例 5:重复冻结 400。
     */
    @Test
    void repeatFreezeRejected() {
        String admin = httpLogin("admin", "admin123");
        assertFreeze(admin, "FRZ-B1", "质检扣留");
        ResponseEntity<Map> res = freeze(admin, "FRZ-B1", "再次冻结");
        assertEquals(400, res.getStatusCode().value());
        assertTrue(messageOf(res).contains("已冻结"), "应提示批次已冻结: " + messageOf(res));
    }

    /**
     * 用例 6:未冻结批次解冻 400。
     */
    @Test
    void unfreezeNotFrozenRejected() {
        String admin = httpLogin("admin", "admin123");
        ResponseEntity<Map> res = unfreeze(admin, "FRZ-B1");
        assertEquals(400, res.getStatusCode().value());
        assertTrue(messageOf(res).contains("未冻结"), "应提示批次未冻结: " + messageOf(res));
    }

    /**
     * 用例 7:冻结不影响入库(入库到冻结批次成功,冻结位保持)。
     */
    @Test
    void freezeDoesNotBlockInbound() {
        String admin = httpLogin("admin", "admin123");
        assertFreeze(admin, "FRZ-B1", "质检扣留");

        inbound(itemId, "FRZ-B1", "3", 30);
        StockDO b1 = stockByBatch("FRZ-B1");
        assertEquals(0, new BigDecimal("13").compareTo(b1.getQuantity()));
        assertEquals(Boolean.TRUE, b1.getFrozen());
    }

    /**
     * 用例 8:冻结记录 list 有 freeze+unfreeze 两条(带原因/操作人/物品回填)。
     */
    @Test
    void freezeLogsList() {
        String admin = httpLogin("admin", "admin123");
        assertFreeze(admin, "FRZ-B1", "质检扣留");
        assertEquals(200, unfreeze(admin, "FRZ-B1").getStatusCode().value());

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(admin);
        ResponseEntity<Map> res = rest.exchange("/api/v1/stock/freeze-logs?warehouseId="
                + warehouseId + "&batchNo=FRZ-B1&page=1&pageSize=20",
                HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        assertEquals(200, res.getStatusCode().value());
        Map<String, Object> body = res.getBody();
        assertNotNull(body);
        assertEquals(2L, ((Number) body.get("total")).longValue());
        List<Map<String, Object>> rows = (List<Map<String, Object>>) body.get("rows");
        assertEquals(2, rows.size());
        // 时间倒序:第一条应为解冻
        assertEquals("unfreeze", rows.get(0).get("action"));
        assertEquals("freeze", rows.get(1).get("action"));
        assertEquals("质检扣留", rows.get(1).get("reason"));
        assertEquals("FRZ-IT", rows.get(1).get("itemCode"));
        assertEquals("admin", rows.get(1).get("operator"));
    }

    /**
     * 用例 9:viewer 冻结 403(无 stock:freeze 权限码)。
     */
    @Test
    void viewerFreezeForbidden() {
        String viewer = httpLogin("lisi", "lisi123");
        ResponseEntity<Map> res = freeze(viewer, "FRZ-B1", "越权冻结");
        assertEquals(403, res.getStatusCode().value());
    }

    /**
     * 用例 10:不存在的批次冻结 400。
     */
    @Test
    void freezeMissingBatchRejected() {
        String admin = httpLogin("admin", "admin123");
        ResponseEntity<Map> res = freeze(admin, "NO-SUCH-BATCH", "原因");
        assertEquals(400, res.getStatusCode().value());
        assertTrue(messageOf(res).contains("批次不存在"), "应提示批次不存在: " + messageOf(res));
    }

    /**
     * 入库造数(服务层)。
     *
     * @param targetItemId 物品 ID
     * @param batchNo      批次号
     * @param qty          数量
     * @param expiryDays   保质期天数
     */
    private void inbound(long targetItemId, String batchNo, String qty, long expiryDays) {
        StockLine line = new StockLine();
        line.setItemId(targetItemId);
        line.setQty(new BigDecimal(qty));
        line.setBatchNo(batchNo);
        line.setExpiryDate(LocalDate.now().plusDays(expiryDays));
        StockOpRequest request = new StockOpRequest();
        request.setWarehouseId(warehouseId);
        request.setDocNo("FRZ-IN-" + batchNo);
        request.setOperator("frz_test");
        request.setLines(List.of(line));
        stockCoreService.inbound(request);
    }

    /**
     * 查指定批次的库存行。
     *
     * @param batchNo 批次号
     * @return 库存行
     */
    private StockDO stockByBatch(String batchNo) {
        Long batchId = jdbcTemplate.queryForObject(
                "SELECT id FROM batch WHERE batch_no = ?", Long.class, batchNo);
        return stockMapper.selectOne(new LambdaQueryWrapper<StockDO>()
                .eq(StockDO::getWarehouseId, warehouseId)
                .eq(StockDO::getBatchId, batchId));
    }

    /**
     * HTTP 冻结(断言 200)。
     *
     * @param token   token
     * @param batchNo 批次号
     * @param reason  原因
     */
    private void assertFreeze(String token, String batchNo, String reason) {
        assertEquals(200, freeze(token, batchNo, reason).getStatusCode().value());
    }

    /**
     * HTTP 冻结。
     *
     * @param token   token
     * @param batchNo 批次号
     * @param reason  原因
     * @return 响应
     */
    private ResponseEntity<Map> freeze(String token, String batchNo, String reason) {
        return post("/api/v1/stock/freeze", token,
                "{\"warehouseId\":" + warehouseId + ",\"batchNo\":\"" + batchNo
                        + "\",\"reason\":\"" + reason + "\"}");
    }

    /**
     * HTTP 解冻。
     *
     * @param token   token
     * @param batchNo 批次号
     * @return 响应
     */
    private ResponseEntity<Map> unfreeze(String token, String batchNo) {
        return post("/api/v1/stock/unfreeze", token,
                "{\"warehouseId\":" + warehouseId + ",\"batchNo\":\"" + batchNo + "\"}");
    }

    /**
     * HTTP 新建出库单(库存扣减同事务)。
     *
     * @param token   token
     * @param target  物品 ID
     * @param qty     数量
     * @param batchNo 批次号(可空=FEFO 自动选批)
     * @return 响应
     */
    private ResponseEntity<Map> outbound(String token, long target, String qty, String batchNo) {
        String line = "{\"itemId\":" + target + ",\"qty\":" + qty
                + (batchNo == null ? "" : ",\"batchNo\":\"" + batchNo + "\"") + "}";
        return post("/api/v1/outbound", token,
                "{\"warehouseId\":" + warehouseId + ",\"items\":[" + line + "]}");
    }

    /**
     * JSON POST。
     *
     * @param path  路径
     * @param token token
     * @param body  请求体
     * @return 响应
     */
    private ResponseEntity<Map> post(String path, String token, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
    }

    /**
     * 取错误体 message。
     *
     * @param res 响应
     * @return message(可空)
     */
    private String messageOf(ResponseEntity<Map> res) {
        Map<String, Object> body = res.getBody();
        return body == null ? "" : String.valueOf(body.get("message"));
    }

    /**
     * 登录并返回 token。
     *
     * @param username 用户名
     * @param password 密码
     * @return token
     */
    @SuppressWarnings("unchecked")
    private String httpLogin(String username, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
        ResponseEntity<Map> res = rest.exchange("/api/v1/auth/login", HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class);
        assertEquals(200, res.getStatusCode().value());
        Map<String, Object> json = res.getBody();
        return json == null ? null : String.valueOf(json.get("token"));
    }

}
