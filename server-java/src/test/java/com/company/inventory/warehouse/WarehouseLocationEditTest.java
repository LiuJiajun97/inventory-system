package com.company.inventory.warehouse;

import com.company.inventory.common.exception.BizException;
import com.company.inventory.model.dto.location.LocationUpdateDTO;
import com.company.inventory.model.dto.warehouse.WarehouseUpdateDTO;
import com.company.inventory.model.entity.item.ItemDO;
import com.company.inventory.model.entity.location.LocationDO;
import com.company.inventory.model.entity.stock.SerialDO;
import com.company.inventory.model.entity.stock.StockDO;
import com.company.inventory.model.entity.user.UserDO;
import com.company.inventory.model.entity.warehouse.WarehouseDO;
import com.company.inventory.mapper.ItemMapper;
import com.company.inventory.mapper.LocationMapper;
import com.company.inventory.mapper.SerialMapper;
import com.company.inventory.mapper.StockMapper;
import com.company.inventory.mapper.UserMapper;
import com.company.inventory.mapper.WarehouseMapper;
import com.company.inventory.service.LocationService;
import com.company.inventory.service.WarehouseService;
import com.company.inventory.model.vo.location.LocationVO;
import com.company.inventory.model.vo.warehouse.WarehouseVO;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeAll;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 仓库/库位编辑接口测试:
 * 1) 编辑仓库名称成功;
 * 2) WarehouseUpdateDTO 不含 warehouseCode 字段(编译期已保证,此处注释说明);
 * 3) enableLocation 1→0 在有库位时被 400 拦截;
 * 4) enableSerial 1→0 在有序列号台账时被 400 拦截;
 * 5) 停用存在 quantity &gt; 0 的库存时被 400 拦截;
 * 6) 编辑库位名称成功;
 * 7) viewer/operator 调 PUT 收到 403。
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
class WarehouseLocationEditTest {

    /** 仓库服务。 */
    @Autowired
    private WarehouseService warehouseService;
    /** 库位服务。 */
    @Autowired
    private LocationService locationService;
    /** 仓库 Mapper。 */
    @Autowired
    private WarehouseMapper warehouseMapper;
    /** 库位 Mapper。 */
    @Autowired
    private LocationMapper locationMapper;
    /** 物品 Mapper。 */
    @Autowired
    private ItemMapper itemMapper;
    /** 库存 Mapper。 */
    @Autowired
    private StockMapper stockMapper;
    /** 序列号 Mapper。 */
    @Autowired
    private SerialMapper serialMapper;
    /** 用户 Mapper。 */
    @Autowired
    private UserMapper userMapper;
    /** REST 客户端。 */
    @Autowired
    private TestRestTemplate rest;
    /** JDBC。 */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 前置:清库 + 造 viewer/operator/admin 用户(用于权限测试)。
     */
    @BeforeAll
    void cleanDb() {
        String sql = "TRUNCATE \"outbound_doc_item\",\"inbound_doc_item\",\"outbound_doc\",\"inbound_doc\","
                + "\"stock_transaction\",\"stock\",\"serial\",\"batch\",\"location\",\"item\","
                + "\"warehouse\",\"supplier\",\"customer\",\"sys_user\" RESTART IDENTITY CASCADE";
        jdbcTemplate.execute(sql);
        // 用户:admin123 已在 seed 中存在,此处仅补 viewer/operator
        BCryptPasswordEncoder enc = new BCryptPasswordEncoder(4);
        createUser("whedit_viewer", "viewer123", "viewer", enc);
        createUser("whedit_operator", "operator123", "operator", enc);
        createUser("whedit_admin", "admin123", "admin", enc);
    }

    /**
     * 用例 1:编辑仓库名称成功,编码锁死不会变化。
     */
    @Test
    void updateWarehouseNameSuccess() {
        WarehouseDO wh = newWarehouse("WHE-W1", "原材料仓A", true, true, false, false);
        WarehouseVO updated = warehouseService.update(wh.getId(),
                new WarehouseUpdateDTO("原材料仓A-改", null, null, null, null, null, null));
        assertEquals("原材料仓A-改", updated.warehouseName());
        assertEquals("WHE-W1", updated.warehouseCode());
        // 编码未变 → 数据库实码仍是 WHE-W1
        WarehouseDO reload = warehouseMapper.selectById(wh.getId());
        assertEquals("WHE-W1", reload.getWarehouseCode());
        // 注:WarehouseUpdateDTO record 编译期即不含 warehouseCode,前端调用方无法误传
    }

    /**
     * 用例 2:空 DTO(全 null)应被 400 拒绝。
     */
    @Test
    void updateEmptyDtoRejected() {
        WarehouseDO wh = newWarehouse("WHE-W2", "原材料仓B", false, false, false, false);
        assertThrows(BizException.class, () -> warehouseService.update(wh.getId(),
                new WarehouseUpdateDTO(null, null, null, null, null, null, null)));
    }

    /**
     * 用例 3:enableLocation 1→0 但该仓已有库位 → 400 拦截。
     */
    @Test
    void disableLocationRejectedWhenLocationsExist() {
        WarehouseDO wh = newWarehouse("WHE-W3", "原材料仓C", false, false, false, true);
        newLocation(wh.getId(), "A-01", "A 区 1 号");
        BizException ex = assertThrows(BizException.class, () -> warehouseService.update(wh.getId(),
                new WarehouseUpdateDTO(null, null, null, null, null, false, null)));
        assertTrue(ex.getMessage().contains("库位"), "message 应含'库位': " + ex.getMessage());
    }

    /**
     * 用例 4:enableLocation 1→0 但该仓库存行 locationId≠0 → 400 拦截。
     */
    @Test
    void disableLocationRejectedWhenStockHasLocation() {
        WarehouseDO wh = newWarehouse("WHE-W4", "原材料仓D", false, false, false, true);
        LocationDO loc = newLocation(wh.getId(), "B-01", "B 区 1 号");
        ItemDO item = newItem("WHE-IT-1");
        // 走库位的库存行
        StockDO s = new StockDO();
        s.setWarehouseId(wh.getId());
        s.setItemId(item.getId());
        s.setBatchId(0L);
        s.setLocationId(loc.getId());
        s.setQuantity(new BigDecimal("5"));
        s.setPreAllocatedQty(BigDecimal.ZERO);
        s.setUpdatedAt(LocalDateTime.now());
        stockMapper.insert(s);
        BizException ex = assertThrows(BizException.class, () -> warehouseService.update(wh.getId(),
                new WarehouseUpdateDTO(null, null, null, null, null, false, null)));
        assertTrue(ex.getMessage().contains("库位"), "message 应含'库位': " + ex.getMessage());
    }

    /**
     * 用例 5:enableSerial 1→0 但该仓已有序列号台账 → 400 拦截。
     */
    @Test
    void disableSerialRejectedWhenSerialsExist() {
        WarehouseDO wh = newWarehouse("WHE-W5", "原材料仓E", false, false, true, false);
        ItemDO item = newItem("WHE-IT-2");
        SerialDO serial = new SerialDO();
        serial.setItemId(item.getId());
        serial.setSerialNo("WHE-SN-001");
        serial.setWarehouseId(wh.getId());
        serial.setStatus("in_stock");
        serial.setInboundTime(LocalDateTime.now());
        serialMapper.insert(serial);
        BizException ex = assertThrows(BizException.class, () -> warehouseService.update(wh.getId(),
                new WarehouseUpdateDTO(null, null, null, null, false, null, null)));
        assertTrue(ex.getMessage().contains("序列号"), "message 应含'序列号': " + ex.getMessage());
    }

    /**
     * 用例 6:停用存在 quantity&gt;0 的库存的仓 → 400 拦截。
     */
    @Test
    void disableActiveWarehouseRejected() {
        WarehouseDO wh = newWarehouse("WHE-W6", "原材料仓F", false, false, false, false);
        ItemDO item = newItem("WHE-IT-3");
        StockDO s = new StockDO();
        s.setWarehouseId(wh.getId());
        s.setItemId(item.getId());
        s.setBatchId(0L);
        s.setLocationId(0L);
        s.setQuantity(new BigDecimal("10"));
        s.setPreAllocatedQty(BigDecimal.ZERO);
        s.setUpdatedAt(LocalDateTime.now());
        stockMapper.insert(s);
        BizException ex = assertThrows(BizException.class, () -> warehouseService.update(wh.getId(),
                new WarehouseUpdateDTO(null, null, null, null, null, null, 0)));
        assertTrue(ex.getMessage().contains("库存"), "message 应含'库存': " + ex.getMessage());
    }

    /**
     * 用例 7:停用无库存的仓 → 成功。
     */
    @Test
    void disableEmptyWarehouseOk() {
        WarehouseDO wh = newWarehouse("WHE-W7", "原材料仓G", false, false, false, false);
        WarehouseVO updated = warehouseService.update(wh.getId(),
                new WarehouseUpdateDTO(null, null, null, null, null, null, 0));
        assertEquals(0, updated.status());
    }

    /**
     * 用例 8:编辑库位名称成功,编码/所属仓库锁死不变。
     */
    @Test
    void updateLocationNameSuccess() {
        WarehouseDO wh = newWarehouse("WHE-W8", "原材料仓H", false, false, false, true);
        LocationDO loc = newLocation(wh.getId(), "C-01", "C 区 1 号");
        LocationVO vo = locationService.update(loc.getId(), new LocationUpdateDTO("C 区 1 号-改"));
        assertEquals("C 区 1 号-改", vo.locationName());
        // 编码/所属仓库未变
        LocationDO reload = locationMapper.selectById(loc.getId());
        assertEquals("C-01", reload.getLocationCode());
        assertEquals(wh.getId(), reload.getWarehouseId());
    }

    /**
     * 用例 9:viewer 调 PUT → 403。
     */
    @Test
    void viewerPutWarehouseForbidden() {
        WarehouseDO wh = newWarehouse("WHE-W9", "原材料仓I", false, false, false, false);
        String token = login("whedit_viewer", "viewer123");
        ResponseEntity<Map> res = putWarehouse(token, wh.getId(),
                "{\"warehouse_name\":\"试图改\"}");
        assertEquals(403, res.getStatusCode().value());
    }

    /**
     * 用例 10:operator 调 PUT → 403(仓库写操作仅 admin)。
     */
    @Test
    void operatorPutWarehouseForbidden() {
        WarehouseDO wh = newWarehouse("WHE-W10", "原材料仓J", false, false, false, false);
        String token = login("whedit_operator", "operator123");
        ResponseEntity<Map> res = putWarehouse(token, wh.getId(),
                "{\"warehouse_name\":\"试图改\"}");
        assertEquals(403, res.getStatusCode().value());
    }

    /**
     * 造仓库。
     */
    private WarehouseDO newWarehouse(String code, String name,
            boolean batch, boolean expiry, boolean serial, boolean location) {
        WarehouseDO wh = new WarehouseDO();
        wh.setWarehouseCode(code);
        wh.setWarehouseName(name);
        wh.setWarehouseType("raw");
        wh.setEnableBatch(batch);
        wh.setEnableExpiry(expiry);
        wh.setEnableSerial(serial);
        wh.setEnableLocation(location);
        wh.setStatus(1);
        warehouseMapper.insert(wh);
        return wh;
    }

    /**
     * 造库位。
     */
    private LocationDO newLocation(long warehouseId, String code, String name) {
        LocationDO loc = new LocationDO();
        loc.setWarehouseId(warehouseId);
        loc.setLocationCode(code);
        loc.setLocationName(name);
        locationMapper.insert(loc);
        return loc;
    }

    /**
     * 造物品。
     */
    private ItemDO newItem(String code) {
        ItemDO item = new ItemDO();
        item.setItemCode(code);
        item.setItemName(code + " 名称");
        item.setUnit("件");
        itemMapper.insert(item);
        return item;
    }

    /**
     * 造用户。
     */
    private void createUser(String username, String pwd, String role, BCryptPasswordEncoder enc) {
        UserDO user = new UserDO();
        user.setUsername(username);
        user.setPasswordHash(enc.encode(pwd));
        user.setName(username + "-显示名");
        user.setRole(role);
        user.setStatus(1);
        userMapper.insert(user);
    }

    /**
     * 登录返回 token。
     */
    @SuppressWarnings("unchecked")
    private String login(String username, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
        ResponseEntity<Map> res = rest.exchange("/api/v1/auth/login", HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class);
        assertEquals(200, res.getStatusCode().value());
        Map<String, Object> json = (Map<String, Object>) res.getBody();
        return json == null ? null : String.valueOf(json.get("token"));
    }

    /**
     * PUT 仓库(走真实 HTTP,覆盖 @RequireRole + JwtInterceptor 全链路)。
     */
    private ResponseEntity<Map> putWarehouse(String token, long id, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return rest.exchange("/api/v1/warehouses/" + id, HttpMethod.PUT,
                new HttpEntity<>(body, headers), Map.class);
    }
}