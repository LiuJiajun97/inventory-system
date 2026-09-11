package com.company.inventory.purchase;

import com.company.inventory.common.exception.BizException;
import com.company.inventory.config.RequireRole;
import com.company.inventory.model.dto.purchase.PurchaseOrderCreateDTO;
import com.company.inventory.model.dto.purchase.PurchaseOrderLineDTO;
import com.company.inventory.model.entity.item.ItemDO;
import com.company.inventory.model.entity.supplier.SupplierDO;
import com.company.inventory.model.entity.user.UserDO;
import com.company.inventory.mapper.ItemMapper;
import com.company.inventory.mapper.SupplierMapper;
import com.company.inventory.mapper.UserMapper;
import com.company.inventory.service.PurchaseOrderService;
import com.company.inventory.model.vo.purchase.PurchaseOrderVO;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 审批权限完善测试(一期收尾 bug#1):operator 可审批他人单、禁自批保持、
 * 五个单据模块的审批/驳回/作废/关闭接口注解放开 admin+operator。
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
class ApprovalPermissionTest {

    /** 采购订单服务 */
    @Autowired
    private PurchaseOrderService purchaseOrderService;
    /** 供应商 Mapper */
    @Autowired
    private SupplierMapper supplierMapper;
    /** 物品 Mapper */
    @Autowired
    private ItemMapper itemMapper;
    /** 用户 Mapper */
    @Autowired
    private UserMapper userMapper;
    /** JDBC */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** operator 制单人用户 ID */
    private long operatorAId;
    /** operator 审批人(他人)用户名 */
    private String operatorB;
    /** admin 用户 ID(自批用例用) */
    private long adminId;
    /** 供应商 */
    private long supplierId;
    /** 物品 */
    private long itemId;

    /**
     * 前置:造两个 operator 用户 + 供应商 + 物品。
     */
    @BeforeAll
    void setup() {
        String suffix = String.valueOf(System.nanoTime());
        SupplierDO sp = new SupplierDO();
        sp.setSupplierCode("APSP-" + suffix);
        sp.setSupplierName("审批测试供应商");
        sp.setStatus(1);
        supplierMapper.insert(sp);
        supplierId = sp.getId();
        ItemDO item = new ItemDO();
        item.setItemCode("APIT-" + suffix);
        item.setItemName("审批测试物");
        item.setUnit("件");
        itemMapper.insert(item);
        itemId = item.getId();
        operatorAId = insertUser("ap_op_a_" + suffix, "操作员甲", "operator");
        operatorB = "ap_op_b_" + suffix;
        insertUser(operatorB, "操作员乙", "operator");
        adminId = insertUser("ap_admin_" + suffix, "管理员甲", "admin");
    }

    /**
     * 用例 1:operator 可审批他人单通过(bug#1 死锁修复),
     * 且五个单据模块的审批类接口注解均放开 operator。
     */
    @Test
    void operatorCanApproveOthersDoc() {
        long id = createOrder();
        purchaseOrderService.submit(id, username(operatorAId));
        PurchaseOrderVO approved = purchaseOrderService.approve(id, operatorB);
        assertEquals("approved", approved.status());
        assertEquals(operatorB, approved.approver());
        // 接口注解同步放开:审批/驳回/作废/关闭均含 operator
        assertTrue(roleOf("PurchaseOrderController", "approve").contains("operator"));
        assertTrue(roleOf("PurchaseOrderController", "reject").contains("operator"));
        assertTrue(roleOf("PurchaseOrderController", "voidDoc").contains("operator"));
        assertTrue(roleOf("PurchaseOrderController", "close").contains("operator"));
        assertTrue(roleOf("SalesOrderController", "approve").contains("operator"));
        assertTrue(roleOf("SalesOrderController", "reject").contains("operator"));
        assertTrue(roleOf("TransferController", "approve").contains("operator"));
        assertTrue(roleOf("TransferController", "voidDoc").contains("operator"));
        assertTrue(roleOf("StocktakeController", "approve").contains("operator"));
        assertTrue(roleOf("StocktakeController", "voidDoc").contains("operator"));
        assertTrue(roleOf("StockAdjustController", "approve").contains("operator"));
        assertTrue(roleOf("StockAdjustController", "voidDoc").contains("operator"));
    }

    /**
     * 用例 2:operator 审批自己单被拒(禁自批校验保持),单据仍 pending。
     */
    @Test
    void operatorApproveOwnDocRejected() {
        long id = createOrder();
        String me = username(operatorAId);
        purchaseOrderService.submit(id, me);
        assertThrows(BizException.class, () -> purchaseOrderService.approve(id, me));
        assertEquals("pending", purchaseOrderService.get(id).status());
    }

    /**
     * 用例 3:admin 可审批自己制的单(2026-09-11 口径:管理员可自批)。
     */
    @Test
    void adminCanApproveOwnDoc() {
        String adminName = username(adminId);
        PurchaseOrderVO vo = purchaseOrderService.create(new PurchaseOrderCreateDTO(LocalDate.now(),
                supplierId, adminId, null, null,
                List.of(new PurchaseOrderLineDTO(itemId, new BigDecimal("1"), null,
                        new BigDecimal("10"), new BigDecimal("13.00"), null))), adminName);
        purchaseOrderService.submit(vo.id(), adminName);
        PurchaseOrderVO approved = purchaseOrderService.approve(vo.id(), adminName);
        assertEquals("approved", approved.status());
        assertEquals(adminName, approved.approver());
    }

    /**
     * 新建单行采购订单(草稿,制单人=operatorA)。
     *
     * @return 订单 ID
     */
    private long createOrder() {
        PurchaseOrderVO vo = purchaseOrderService.create(new PurchaseOrderCreateDTO(LocalDate.now(),
                supplierId, operatorAId, null, null,
                List.of(new PurchaseOrderLineDTO(itemId, new BigDecimal("1"), null,
                        new BigDecimal("10"), new BigDecimal("13.00"), null))), username(operatorAId));
        return vo.id();
    }

    /**
     * 取用户名字。
     *
     * @param userId 用户 ID
     * @return 用户名字
     */
    private String username(long userId) {
        return userMapper.selectById(userId).getUsername();
    }

    /**
     * 造用户。
     *
     * @param u 用户名
     * @param name 姓名
     * @param role 角色
     * @return 用户 ID
     */
    private long insertUser(String u, String name, String role) {
        UserDO user = new UserDO();
        user.setUsername(u);
        user.setPasswordHash("$2a$10$dummy");
        user.setName(name);
        user.setRole(role);
        user.setStatus(1);
        userMapper.insert(user);
        return user.getId();
    }

    /**
     * 反射读取 Controller 指定方法的 @RequireRole 角色集合。
     *
     * @param controllerName 控制器类简名
     * @param methodName     方法名
     * @return 角色列表
     */
    private List<String> roleOf(String controllerName, String methodName) {
        try {
            Class<?> clazz = Class.forName("com.company.inventory.controller." + controllerName);
            for (Method m : clazz.getDeclaredMethods()) {
                if (m.getName().equals(methodName)) {
                    RequireRole rr = m.getAnnotation(RequireRole.class);
                    if (rr != null) {
                        return Arrays.asList(rr.value());
                    }
                }
            }
            throw new IllegalStateException("方法或注解缺失: " + controllerName + "#" + methodName);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("类不存在: " + controllerName, e);
        }
    }
}
