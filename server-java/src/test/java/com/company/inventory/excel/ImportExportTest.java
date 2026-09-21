package com.company.inventory.excel;

import com.alibaba.excel.EasyExcel;
import com.company.inventory.common.support.AuthCache;
import com.company.inventory.model.dto.excel.CustomerImportRow;
import com.company.inventory.model.dto.excel.ItemImportRow;
import com.company.inventory.model.dto.excel.SupplierImportRow;
import com.company.inventory.model.dto.item.ItemCreateDTO;
import com.company.inventory.model.vo.excel.ItemExportRow;
import com.company.inventory.model.vo.supplier.SupplierVO;
import com.company.inventory.model.query.SupplierQuery;
import com.company.inventory.model.vo.customer.CustomerVO;
import com.company.inventory.model.query.CustomerQuery;
import com.company.inventory.service.CustomerService;
import com.company.inventory.service.ItemService;
import com.company.inventory.service.SupplierService;
import com.company.inventory.model.entity.user.UserDO;

import org.junit.jupiter.api.AfterAll;
import com.company.inventory.support.RbacSeedSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V12 导入导出测试:物品/供应商/客户 xlsx 导入(部分失败/本批重复/字段回读)、
 * 物品/库存 xlsx 导出(magic number + 行数)、operator 导入 403。
 *
 * <p>自包含:自建测试用户(legacy role 列)与 IE- 前缀主数据,AfterAll 全删,
 * 不动 RBAC 表(不 TRUNCATE,避免波及其他测试类)。</p>
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
class ImportExportTest {

    /** 测试密码明文。 */
    private static final String PW = "ie123456";

    /** REST 客户端。 */
    @Autowired
    private TestRestTemplate rest;

    /** JDBC(清理用)。 */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 权限缓存(测试前清缓存防串号)。 */
    @Autowired
    private AuthCache authCache;

    /** 物品服务(造前置数据)。 */
    @Autowired
    private ItemService itemService;

    /** 供应商服务(回读用)。 */
    @Autowired
    private SupplierService supplierService;

    /** 客户服务(回读用)。 */
    @Autowired
    private CustomerService customerService;

    /** admin token。 */
    private String adminToken;

    /** operator token。 */
    private String operatorToken;

    /**
     * 前置:注入 RBAC 基线 + 清权限缓存,造 admin/operator 用户(legacy role 列)与物品前置数据
     * (IE-DUP-001 供重复用例,IE-EXP-001/002 供导出行数用例)。
     */
    @BeforeAll
    void setUp() {
        RbacSeedSupport.injectBaseline(jdbcTemplate);
        RbacSeedSupport.evictAuthCache(authCache);
        createLegacyUser("ie_admin", "admin");
        createLegacyUser("ie_op", "operator");
        RbacSeedSupport.bindUserRole(jdbcTemplate, "ie_admin");
        RbacSeedSupport.bindUserRole(jdbcTemplate, "ie_op");
        adminToken = login("ie_admin");
        operatorToken = login("ie_op");

        itemService.create(new ItemCreateDTO("IE-DUP-001", "重复编码物品", "支", null,
                null, null, null, null));
        itemService.create(new ItemCreateDTO("IE-EXP-001", "导出物品一", "支", "E1",
                null, "hardware", null, null));
        itemService.create(new ItemCreateDTO("IE-EXP-002", "导出物品二", "套", "E2",
                null, "raw", null, null));
    }

    /**
     * 后置:删除本类全部 IE- 前缀数据与测试用户。
     */
    @AfterAll
    void tearDown() {
        jdbcTemplate.update("DELETE FROM item WHERE item_code LIKE 'IE-%'");
        jdbcTemplate.update("DELETE FROM supplier WHERE supplier_code LIKE 'IE-%'");
        jdbcTemplate.update("DELETE FROM customer WHERE customer_code LIKE 'IE-%'");
        RbacSeedSupport.unbindUser(jdbcTemplate, "ie_admin");
        RbacSeedSupport.unbindUser(jdbcTemplate, "ie_op");
        jdbcTemplate.update("DELETE FROM sys_user WHERE username IN ('ie_admin', 'ie_op')");
    }

    /**
     * 物品导入:3 行(2 成功 + 1 与库中已有重复)→ imported=2,failed 含行号/原因。
     */
    @Test
    @SuppressWarnings("unchecked")
    void itemImport_partialFailure() {
        List<ItemImportRow> rows = List.of(
                itemRow("IE-IMP-001", "导入物品一", "支"),
                itemRow("IE-IMP-002", "导入物品二", "套"),
                itemRow("IE-DUP-001", "与库中重复", "支"));
        Map<String, Object> body = postImport("/api/v1/items/import", adminToken,
                "items.xlsx", buildXlsx(ItemImportRow.class, rows));

        assertEquals(2, ((Number) body.get("imported")).intValue());
        List<Map<String, Object>> failed = (List<Map<String, Object>>) body.get("failed");
        assertEquals(1, failed.size());
        assertEquals(4, failed.get(0).get("row"));
        assertEquals("IE-DUP-001", failed.get(0).get("code"));
        assertTrue(String.valueOf(failed.get(0).get("reason")).contains("已存在"),
                "重复原因应提示已存在: " + failed.get(0).get("reason"));

        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM item WHERE item_code IN ('IE-IMP-001', 'IE-IMP-002')",
                Integer.class));
    }

    /**
     * 物品导入:本批内编码重复 → 第二行失败,第一行已导入。
     */
    @Test
    @SuppressWarnings("unchecked")
    void itemImport_batchDuplicate() {
        List<ItemImportRow> rows = List.of(
                itemRow("IE-IMP-B1", "批内重复一", "支"),
                itemRow("IE-IMP-B1", "批内重复二", "套"));
        Map<String, Object> body = postImport("/api/v1/items/import", adminToken,
                "items.xlsx", buildXlsx(ItemImportRow.class, rows));

        assertEquals(1, ((Number) body.get("imported")).intValue());
        List<Map<String, Object>> failed = (List<Map<String, Object>>) body.get("failed");
        assertEquals(1, failed.size());
        assertEquals(3, failed.get(0).get("row"));
        assertTrue(String.valueOf(failed.get(0).get("reason")).contains("已存在"));
    }

    /**
     * 供应商/客户导入各 1 行成功,回读含 V9/V10 新字段(bankAccount/email/payTermDays)。
     */
    @Test
    @SuppressWarnings("unchecked")
    void supplierCustomerImport_fieldRoundTrip() {
        SupplierImportRow s = new SupplierImportRow();
        s.setSupplierCode("IE-SUP-001");
        s.setSupplierName("导入供应商");
        s.setContact("张三");
        s.setPhone("13800000001");
        s.setAddress("温州示例路 1 号");
        s.setSettleMethod("月结");
        s.setDefaultTaxRate("9");
        s.setTaxNo("91330000TESTSUP001");
        s.setEmail("ie-sup@example.com");
        s.setBankName("工商银行测试支行");
        s.setBankAccount("622202IE0001");
        s.setCreditLimit("50000");
        s.setPayTermDays("30");
        s.setDeliveryAddress("温州交货仓");
        Map<String, Object> sBody = postImport("/api/v1/suppliers/import", adminToken,
                "suppliers.xlsx", buildXlsx(SupplierImportRow.class, List.of(s)));
        assertEquals(1, ((Number) sBody.get("imported")).intValue());
        assertEquals(List.of(), sBody.get("failed"));

        CustomerImportRow c = new CustomerImportRow();
        c.setCustomerCode("IE-CUS-001");
        c.setCustomerName("导入客户");
        c.setContact("李四");
        c.setPhone("13900000001");
        c.setAddress("苏州示例路 2 号");
        c.setSettleMethod("预付");
        c.setDefaultTaxRate("13");
        c.setTaxNo("91320000TESTCUS001");
        c.setEmail("ie-cus@example.com");
        c.setBankName("建设银行测试支行");
        c.setBankAccount("621700IE0002");
        c.setCreditLimit("20000");
        c.setPayTermDays("0");
        c.setDeliveryAddress("苏州交货仓");
        Map<String, Object> cBody = postImport("/api/v1/customers/import", adminToken,
                "customers.xlsx", buildXlsx(CustomerImportRow.class, List.of(c)));
        assertEquals(1, ((Number) cBody.get("imported")).intValue());
        assertEquals(List.of(), cBody.get("failed"));

        // 回读供应商(中文"月结"应映射为 month)
        SupplierQuery sq = new SupplierQuery();
        sq.setKeyword("IE-SUP-001");
        SupplierVO sv = supplierService.list(sq).rows().get(0);
        assertEquals("month", sv.settleMethod());
        assertEquals("622202IE0001", sv.bankAccount());
        assertEquals("ie-sup@example.com", sv.email());
        assertEquals(30, sv.payTermDays());
        assertEquals("工商银行测试支行", sv.bankName());

        // 回读客户
        CustomerQuery cq = new CustomerQuery();
        cq.setKeyword("IE-CUS-001");
        CustomerVO cv = customerService.list(cq).rows().get(0);
        assertEquals("prepay", cv.settleMethod());
        assertEquals("621700IE0002", cv.bankAccount());
        assertEquals("ie-cus@example.com", cv.email());
    }

    /**
     * 物品导出:GET 返回 xlsx(PK magic)+ 行数 = keyword 命中行数,表头中文。
     */
    @Test
    void itemExport_xlsxWithRows() {
        ResponseEntity<byte[]> resp = getBinary(adminToken,
                "/api/v1/items/export?keyword=IE-EXP");
        assertEquals(200, resp.getStatusCode().value());
        assertNotNull(resp.getBody());
        String contentType = String.valueOf(resp.getHeaders().getFirst("Content-Type"));
        assertTrue(contentType.contains("spreadsheetml.sheet"), contentType);
        String disposition = String.valueOf(resp.getHeaders().getFirst("Content-Disposition"));
        assertTrue(disposition.contains("attachment"), "应为附件下载: " + disposition);
        assertTrue(disposition.contains("filename*=UTF-8''"), "应带中文文件名: " + disposition);

        byte[] bytes = resp.getBody();
        assertEquals('P', bytes[0]);
        assertEquals('K', bytes[1]);

        // EasyExcel 回读校验行数与内容
        List<ItemExportRow> rows = EasyExcel.read(new ByteArrayInputStream(bytes))
                .head(ItemExportRow.class).sheet().doReadSync();
        assertEquals(2, rows.size());
        assertTrue(rows.stream().anyMatch(r -> "IE-EXP-001".equals(r.getItemCode())));
        assertTrue(rows.stream().anyMatch(r -> "IE-EXP-002".equals(r.getItemCode())));
    }

    /**
     * 库存导出:200 + xlsx(PK magic,空数据也含表头)。
     */
    @Test
    void stockExport_xlsx() {
        ResponseEntity<byte[]> resp = getBinary(adminToken, "/api/v1/stock/export");
        assertEquals(200, resp.getStatusCode().value());
        assertNotNull(resp.getBody());
        assertEquals('P', resp.getBody()[0]);
        assertEquals('K', resp.getBody()[1]);
    }

    /**
     * operator 导入 403(导入仅 admin)。
     */
    @Test
    void operatorImport_forbidden() {
        List<ItemImportRow> rows = List.of(itemRow("IE-IMP-OP", "越权导入", "支"));
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", fileNameResource("items.xlsx",
                buildXlsx(ItemImportRow.class, rows)));
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(operatorToken);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<Map> res = rest.exchange("/api/v1/items/import", HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class);
        assertEquals(403, res.getStatusCode().value(), "operator 导入应 403");
    }

    /**
     * 造 legacy 格式用户(仅 role 列,无 sys_user_role,登录时回退读该列)。
     *
     * @param username 用户名
     * @param role     角色编码
     */
    private void createLegacyUser(String username, String role) {
        UserDO user = new UserDO();
        user.setUsername(username);
        user.setPasswordHash(new BCryptPasswordEncoder(4).encode(PW));
        user.setName(username);
        user.setRole(role);
        user.setStatus(1);
        jdbcTemplate.update(
                "INSERT INTO sys_user (username, password_hash, name, role, status, created_at) "
                        + "VALUES (?, ?, ?, ?, 1, now())",
                username, user.getPasswordHash(), username, role);
    }

    /**
     * 登录并返回 token。
     *
     * @param username 用户名
     * @return JWT
     */
    @SuppressWarnings("unchecked")
    private String login(String username) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> res = rest.exchange("/api/v1/auth/login", HttpMethod.POST,
                new HttpEntity<>("{\"username\":\"" + username + "\",\"password\":\"" + PW + "\"}",
                        headers), Map.class);
        assertEquals(200, res.getStatusCode().value(), "登录应成功");
        return res.getBody().get("token").toString();
    }

    /**
     * 组装物品导入行。
     *
     * @param code 编码
     * @param name 名称
     * @param unit 单位
     * @return 导入行
     */
    private ItemImportRow itemRow(String code, String name, String unit) {
        ItemImportRow row = new ItemImportRow();
        row.setItemCode(code);
        row.setItemName(name);
        row.setUnit(unit);
        return row;
    }

    /**
     * EasyExcel 写内存 xlsx。
     *
     * @param <T>     行模型类型
     * @param headClass 行模型类
     * @param rows    数据行
     * @return xlsx 字节
     */
    private <T> byte[] buildXlsx(Class<T> headClass, List<T> rows) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        EasyExcel.write(out, headClass).sheet("导入").doWrite(rows);
        return out.toByteArray();
    }

    /**
     * 命名 ByteArrayResource(模拟 multipart 文件名,非 xlsx 扩展名会被拒)。
     *
     * @param filename 文件名
     * @param bytes    内容
     * @return 资源
     */
    private ByteArrayResource fileNameResource(String filename, byte[] bytes) {
        return new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
    }

    /**
     * multipart 上传 xlsx 到导入端点,解析 JSON 响应体。
     *
     * @param path    导入端点
     * @param token   JWT
     * @param filename 文件名
     * @param bytes   xlsx 字节
     * @return 响应体(imported/failed)
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> postImport(String path, String token, String filename,
            byte[] bytes) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", fileNameResource(filename, bytes));
        HttpHeaders headers = new HeadersWithBearer(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<Map> res = rest.exchange(path, HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class);
        assertEquals(200, res.getStatusCode().value(), "导入应 200: " + res.getBody());
        return res.getBody();
    }

    /**
     * 带 Bearer 的 GET,返回二进制体(导出用)。
     *
     * @param token JWT
     * @param path  路径(含查询串)
     * @return 响应
     */
    private ResponseEntity<byte[]> getBinary(String token, String path) {
        HttpHeaders headers = new HeadersWithBearer(token);
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
    }

    /**
     * Bearer 请求头小类(避免匿名内部类噪音)。
     *
     * @author inventory
     */
    private static final class HeadersWithBearer extends HttpHeaders {

        private static final long serialVersionUID = 1L;

        /**
         * 构造带 Bearer 的头部。
         *
         * @param token JWT
         */
        HeadersWithBearer(String token) {
            setBearerAuth(token);
        }
    }
}
