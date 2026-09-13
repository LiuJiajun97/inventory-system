# 库存管理系统

> 前后端分离项目(Java 21 + Spring Boot 3.5 + MyBatis-Plus 3.5 + PostgreSQL 16 / Vite + React 18 + antd 5 + @ant-design/pro-components 2.8 列表页 ProTable、单据表单页 ProForm 表头)。
> 企业级进销存一期:采购/销售/调拨/盘点/调整/预警/审批/预占/退货/期初 + 基础出入库,21 个 Controller,前端 33 个页面(26 个业务模块)。
> 后端按阿里开发规范分层,出库条件 UPDATE 防穿仓、FEFO/FIFO 选批、序列号台账等核心规则零弱化。
> **159 条测试全绿**(114 存量 + 15 RBAC 批 1a + 2 RBAC 批 2 + 4 V9 通用字段补全 + 5 V10 对标字段补齐 + 8 V11 退货 + 6 V12 导入导出 + 5 V13 期初),阿里 checkstyle 规则集(违规 0),前端 build 0 错。
> 时间统一东八区(Asia/Shanghai,JVM 显式锁定),格式 `yyyy-MM-dd HH:mm:ss`(日期 `yyyy-MM-dd`)。

---

## 1. 架构

```
                   ┌──────────────────────┐
浏览器 (antd UI)   │  React SPA           │  端口 5173 (dev)
                   └─────────┬────────────┘
                             │ /api proxy (vite)
                             ▼
                   ┌──────────────────────┐
                   │  Spring Boot 后端    │  端口 8081
                   │  MyBatis-Plus / JWT  │  /api/v1/*
                   │  springdoc → /docs   │
                   └─────────┬────────────┘
                             │ JDBC
                             ▼
                   ┌──────────────────────┐
                   │  PostgreSQL 16       │  端口 5433 (docker)
                   └──────────────────────┘
```

后端分层:`controller → service(/impl) → mapper → model`(model 下 entity(DO)/dto/vo/query 四包,按功能再分文件夹,如 `model/dto/purchase/`)。
出入库/采购/销售/调拨在单个事务内完成(`StockCoreService`),出库扣减为条件 `UPDATE ... WHERE quantity >= qty`,affected rows=0 即"库存不足"整单回滚。

> 说明:PG 库表名/列名统一小写蛇形(`db/V6__snake_case.sql` 迁移),`map-underscore-to-camel-case: true`
> 自动映射 Java camelCase 字段 ↔ snake_case 列;手写 SQL 不写引号。审计四件套(creator/createdAt/updater/updatedAt)
> 由 `AuditMetaObjectHandler` 统一填充,DO 字段仅需 `@TableField(fill = ...)` 标记。

> **文档同步约定**:接口/表结构/测试数量变更时,随代码一起更新本 README,保持文档与代码实时一致。
> 每次实质变更同时追加一条到 `docs/迭代日志.md`(迭代流水账,含验证与遗留项)。

---

## 2. 启动步骤

```bash
# 1. 启动 PG(已起过可跳过)
docker compose up -d

# 2. 推 schema + 迁移 + 种子数据(已推过可跳过)
docker exec -i inventory-postgres psql -U inv -d inventory < server-java/src/main/resources/db/schema.sql
docker exec -i inventory-postgres psql -U inv -d inventory < server-java/src/main/resources/db/V2__phase1.sql
docker exec -i inventory-postgres psql -U inv -d inventory < server-java/src/main/resources/db/V3__dict.sql
docker exec -i inventory-postgres psql -U inv -d inventory < server-java/src/main/resources/db/V4__dict_type.sql
docker exec -i inventory-postgres psql -U inv -d inventory < server-java/src/main/resources/db/V5__audit_fields.sql
docker exec -i inventory-postgres psql -U inv -d inventory < server-java/src/main/resources/db/V6__snake_case.sql
docker exec -i inventory-postgres psql -U inv -d inventory < server-java/src/main/resources/db/V7__rbac.sql
docker exec -i inventory-postgres psql -U inv -d inventory < server-java/src/main/resources/db/seed.sql

# 3. 启动后端(IDEA 运行 InventoryApplication,或命令行)
cd server-java
bash ../scripts/mvn.sh spring-boot:run
# 后端:http://127.0.0.1:8081  Swagger:http://127.0.0.1:8081/docs

# 4. 启动前端
cd web
npm install   # 首次
npm run dev
# 前端:http://localhost:5173 (dev 代理 /api → 8081)

# 5. 测试 & 构建(在 server-java 下)
bash ../scripts/mvn.sh test        # 159 条,全绿,无 skip
bash ../scripts/mvn.sh package     # 0 错误
bash ../scripts/mvn.sh checkstyle:check   # 违规 0
```

> 本机 bash 下 `mvn` 直接跑有 classworlds 路径 bug,构建一律走 `scripts/mvn.sh` 包装脚本。
> 默认种子数据:3 仓库(原材料/成品/五金)+ 4 物品 + 库位 + 3 用户 + 字典 3 组(仓库类型/物品分类/结算方式)。

---

## 3. 三个默认账号

| 用户名 | 密码 | 角色 | 姓名 |
|---|---|---|---|
| `admin` | `admin123` | admin 管理员 | 系统管理员 |
| `zhangsan` | `zhang123` | operator 库员 | 张三 |
| `lisi` | `lisi123` | viewer 查看 | 李四 |

> **⚠️ 上线前必须修改 admin 密码**,可通过 `POST /api/v1/auth/password` 改密。

---

## 4. 角色权限矩阵(后端强制,前端只做菜单显隐)

| 接口组 | admin | operator | viewer |
|---|---|---|---|
| 登录 / 改自己密码 | ✅ | ✅ | ✅ |
| 总览 / 库存查询 / 流水查询 | ✅ | ✅ | ✅ |
| 供应商 / 客户:增改 / 查 | ✅ / 查 | 查 | 查 |
| 物品 / 仓库 / 库位:增改 / 查 | ✅ / 查 | 查 | 查 |
| 入库单 / 出库单 创建 | ✅ | ✅ | ❌ |
| 采购 / 销售 / 调拨 / 盘点 / 调整 单据创建 | ✅ | ✅ | ❌ |
| 采购退货单 / 销售退货单 创建(create 即过账,V11) | ✅ | ✅ | ❌ |
| 期初单 创建(create 即过账,联动期初入库单,V13) | ✅ | ✅ | ❌ |
| 物品 / 供应商 / 客户 xlsx 导入(V12,逐行校验,失败行汇总) | ✅ | ❌ | ❌ |
| 物品 / 供应商 / 客户 / 库存 / 入库 / 出库 / 采购 / 销售 xlsx 导出(V12,与列表读一致) | ✅ | ✅ | ✅ |
| 草稿 / 已驳回单据编辑(采购、销售、调拨、调整,已驳回编辑后回草稿) | ✅ | ✅ | ❌ |
| 单据审批 / 驳回(采购、销售、调拨、盘点、调整) | ✅(可自批) | ✅(禁自批) | ❌ |
| 低库存 / 临期预警 查询 | ✅ | ✅ | ✅ |
| 字典:读 | ✅ | ✅ | ✅ |
| 字典:增删改/停用 | ✅ | ❌ | ❌ |
| 用户管理 | ✅ | ❌ | ❌ |
| 数据权限(10 个库存列表行级过滤,批 2 + V11 退货列表 + V13 期初列表) | 豁免全量 | 仅授权仓 | 仅授权仓(未授权查空) |
| 系统监控 | ✅ | ❌ | ❌ |
| 角色管理(RBAC:角色 CRUD + 角色-菜单分配) | ✅ | ❌ | ❌ |
| 菜单管理(RBAC:菜单树查看/增删改) | ✅ | ❌ | ❌ |

后端用 `JwtInterceptor` + `@RequireRole` 注解:无 token 返回 `401 {statusCode,error,message}`,角色不够返回 `403`,message 为中文。
RBAC(V7 起):JWT claim 由单 `role` 升级为 `roles` 数组(旧单值 token 兼容回退);`@RequireRole` 语义改为"用户角色集与注解有交集即通过";新增 `@RequirePermission("code")` 按钮级权限码注解(权限码 = 用户多角色 sys_role_menu 并集中 type='button' 的 menu_code);`GET /auth/menus` 返回当前用户并集菜单树(仅目录+菜单,附各菜单下按钮权限码)供前端动态导航(前端动态化在批 1b)。用户多角色(sys_user_role 并集),`sys_user.role` 列存量兼容保留(未绑定角色的存量用户登录回退读该列)。
RBAC 批 2(V8 起):用户 API 多角色化(`POST /users` 传 `roleIds` 必填;`PUT /users/:id` 传 `roleIds` 可选 + `warehouseIds` 可选,空列表 = 清空仓库授权);VO 新增 `roles`(编码+名称)与 `warehouseIds`,旧 `role` 字段保留取首角色。数据权限:`JwtInterceptor` 每请求写 `DataScope`(admin 豁免 null / 空 = 查空 / 非空 = 仅授权仓),库存/流水/入库/出库/盘点/调整/调拨 7 个列表接口按授权仓过滤(调拨为源仓或目的仓任一命中;仪表盘/预警/详情暂不纳入)。V8 seed 补系统组按钮码(user/role/menu/dict 共 13 个)+「菜单管理」菜单(/menus,sort 在角色权限之后),全部绑 admin。
批 3 回归验收修复 seed 两处越权:① V7 operator 段原误绑基础数据组(物品/仓库/库位/供应商/客户)15 个按钮码,而后端这些写端点一期起即 `@RequireRole("admin")`,前后端不一致(operator 前端能点、后端 403),现 operator 不再绑基础数据按钮(operator=纯业务 65);② V7 operator/viewer 排除系统组原只排除 `system-dir` 及其直接子菜单,V8 追加的系统组按钮 parent 是二级菜单故未排除,在含 V8 菜单的库上重跑 V7 会把 13 个系统组按钮污染给 operator(后端 `@RequirePermission` 放行=越权),改递归子树排除根治。生产库终态:99 菜单 / 185 绑定(admin 99 / operator 65 / viewer 21)。
审批资格由 `common/support/ApprovalGuard` 统一裁决:**admin 可审批自己提交的单据,operator 禁自批**(防"提交-审批"死锁)。
前端 axios 拦截器:401 自动清 token 跳 `/login`,403 弹错误提示。

---

## 5. API 一览(前缀 `/api/v1`,全中文描述,列表统一 `{rows,total,page,pageSize}`)

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| POST | `/auth/login` `/auth/password` `/auth/me` | 登录 / 改密 / 当前用户(登录响应 user 新增 roles 数组) | 公开 / 登录 |
| GET | `/auth/menus` | 当前用户菜单树(多角色并集,仅目录+菜单,附按钮权限码列表) | 登录 |
| GET | `/auth/perm-check` | 权限码探针(校验 @RequirePermission,码 purchase-order:approve) | 登录+权限码 |
| GET | `/dashboard/summary` | 首页统计 | 登录 |
| GET/POST/PUT | `/warehouses` `/warehouses/:id` | 仓库列表 / 新建 / 编辑(编码不可改,含防不一致校验) | 登录 / admin |
| GET/POST/PUT | `/locations` `/locations/:id` | 库位列表 / 新建 / 编辑(编码与所属仓库不可改) | 登录 / admin |
| GET/POST/PUT | `/items` `/items/:id` | 物品列表 / 新建 / 编辑(编码不可改) | 登录 / admin |
| GET | `/stock` `/stock/batches/:id` | 库存余额 / 批次明细 | 登录 |
| GET | `/transactions` | 流水查询 | 登录 |
| GET/POST | `/inbound` | 手工入库单 | 登录 / admin+operator |
| GET/POST | `/outbound` | 手工出库单 | 登录 / admin+operator |
| GET/POST/PUT | `/suppliers` `/customers` | 供应商 / 客户 | 登录 / admin |
| GET/POST/PUT | `/purchase-orders` `/purchase-orders/:id/...` | 采购订单 + 提交/审批/驳回/到货(PUT 编辑,仅草稿/已驳回) | 登录 / admin+operator |
| GET/POST/PUT | `/sales-orders` `/sales-orders/:id/...` | 销售订单 + 提交/审批/驳回/发货(PUT 编辑,仅草稿/已驳回) | 登录 / admin+operator |
| GET/POST | `/purchase-returns` `/purchase-returns/:id` | 采购退货单(create 即过账,联动出库单 ref_type=purchase_return) | 登录 / admin+operator |
| GET/POST | `/sales-returns` `/sales-returns/:id` | 销售退货单(create 即过账,联动入库单 ref_type=sales_return) | 登录 / admin+operator |
| GET/POST/PUT | `/transfers` | 调拨单(原子一步:出+入同事务;PUT 编辑仅草稿/已驳回) | 登录 / admin+operator |
| GET/POST | `/stocktakes` `/stocktakes/:id/...` | 盘点单 + 录入实盘/审批 | 登录 / admin+operator |
| GET/POST/PUT | `/stock-adjusts` | 库存调整单(盘盈亏/报损,含审批;PUT 编辑仅草稿/已驳回) | 登录 / admin+operator |
| GET | `/alerts/low-stock` `/alerts/expiry` | 低库存 / 临期预警 | 登录 |
| GET | `/dicts` `/dicts/all` | 字典查询(登录 / admin) | 登录 / admin |
| GET/POST/PUT | `/dicts/types` `/dicts/types/:typeCode` | 字典类型列表/新建/编辑(admin 可写) | 登录 / admin |
| POST/PUT/DELETE | `/dicts/admin...` | 字典项增删改/停用(引用校验) | admin |
| GET/POST/PUT | `/users` | 用户管理(多角色 `roleIds` + 仓库授权 `warehouseIds`,空数组 = 清空授权) | admin |
| GET/POST/PUT/DELETE | `/roles` `/roles/:id` | 角色列表 / 详情 / 新建 / 更新(内置禁改码) / 删除(内置/有用户绑定禁删) | admin |
| PUT/GET | `/roles/:id/menus` | 角色-菜单全量分配 / 已绑菜单 ID 回显 | admin |
| GET | `/menus/tree` | 全量菜单树(含 button 子节点,管理页用) | admin |
| POST/PUT/DELETE | `/menus` `/menus/:id` | 菜单新建(code 唯一) / 更新(code 不可改) / 删除(有子节点禁删,连带清 role_menu) | admin |
| GET | `/monitor/overview` | 系统监控(本机 CPU/内存/JVM/磁盘快照,5 秒轮询) | admin |

完整契约以 Swagger 为准:`http://127.0.0.1:8081/docs`。

---

## 6. 核心业务规则

**基础库存(一期前)**
1. **仓库 4 开关**:`enableBatch` / `enableExpiry` / `enableSerial` / `enableLocation` 决定入库/出库必填项,不依赖 `warehouse_type`。
2. **入库**:批次不存在自动创建;启用保质期必须带批次;序列号条数 = 入库数量(整数)。
3. **出库选批**:启用保质期 → **FEFO**(expiryDate 升序,NULL 最后);仅启用批次 → **FIFO**;也可手动指定。
4. **出库扣减**:条件 `UPDATE stock SET quantity = quantity - n WHERE ... AND quantity >= n`,0 行抛"库存不足"整单回滚。**禁止先 SELECT 校验再 UPDATE**(`mapper/StockMapper.xml` 的 `deductStock`,核心逻辑禁止弱化)。
5. **余额唯一键**:`(warehouse, item, batch, location)`,默认 `batch=0, location=0`。
6. **流水**:只插不改,必带 `afterQty`(变动后结存)。
7. **序列号台账**:独立表,出库逐号条件更新,任一失败整单回滚。
8. **整单事务**:单据头 + 行 + 流水 + 余额 + 序列号 同事务。
9. **单据号**:`RK/CK/CG/XS/DB/PD/TZ/CT/XT-YYYYMMDD-NNNN`,按天序列(`DocNoService`);CT=采购退货、XT=销售退货。

**进销存一期**
10. **价税分离**:订单明细 `unitPrice`(不含税)/`taxRate`/`taxAmount`/`totalAmount`(含税),合计 `sum = Σ totalAmount`。
11. **审批流**:单据 `draft → submitted → approved/rejected`,仅 approved 可执行到货/发货;审批资格见 §4(ApprovalGuard)。
12. **采购到货**:可部分到货累计,到齐自动 `completed`;**超收拒绝**(400)。
13. **销售预占**:审批即预占(`Stock.preAllocatedQty` 原子增加),发货时预占与库存同事务扣减,预占不清零则发货失败;审批时做预占可行性校验,不可行直接驳回(如"仍缺 N")。
14. **调拨**:源仓扣减 + 目的仓入库单事务内原子完成,任一失败整体回滚。
15. **盘点/调整**:盘点录入实盘差异 → 生成调整单 → 审批后过账,流水 type 为 `adjust_in/adjust_out`。
16. **预警**:低库存(`minStock` 阈值)与临期(效期 N 天内)只读查询,不自动改库存。
17. **字典**:配置类枚举(仓库类型/物品分类/结算方式)进字典表,类型可动态管理(DictType 表);状态机枚举(单据状态等)不进字典。停用被业务表引用的字典项被拒(400);停用含启用项的类型被拒(400);引用校验通过 `DictReferenceRegistry` 注册表统一管理。
18. **退货(V11)**:独立采购退货单(CT)/销售退货单(XT),**create 即过账**(与出入库单一致,无草稿/作废):单事务内校验原单已审批(含自动 completed/手工 closed)→ 逐行校验可退量(上限=原行已到货/已发货 − 已退累计,超量整单回滚)→ 落退货单头/行(单价/税率锁原行快照,服务端取值)→ 生成出库/入库单走现有过账链路(序列号等仓库配置校验复用)→ 回写原单行 `returned_qty`(销售退货不改 `shipped_qty`,净发货=shipped−returned)。联动出入库单 `ref_type` 落 `purchase_return`/`sales_return`,前端流水/列表中文映射同步。
19. **导入导出(V12)**:EasyExcel 3.3.4(唯一新增依赖)。**导入**(仅 admin):物品/供应商/客户 3 类主数据 xlsx,逐行校验(必填/数值/分类与结算方式中英文映射),成功行走既有 `Service.create`(复用编码/条码查重与审计填充,行级独立事务),失败行汇总进 `{imported, failed:[{row,code,reason}]}`(HTTP 恒 200,非 xlsx/解析失败 400);模板下载 `GET /items|suppliers|customers/template`(表头+1 行示例)。**导出**(与列表读同权限,不分页复用列表 Query DTO + Service 查询):物品/供应商/客户/库存/入库/出库/采购/销售 8 个列表,中文表头,单据一行一单(单号/日期/状态/对方/仓库/金额/备注),库存导出沿用 DataScope 数据权限;响应头 `Content-Disposition: attachment; filename*=UTF-8''<中文名>.xlsx`。按钮码 11 个(V12 迁移:admin 全部/operator 5 个列表导出/viewer 无),前端 `ImportButton`(Upload 自定义请求 + 失败行 Modal)/`ExportButton`(fetch blob 下载)组件。
20. **期初库存(V13)**:系统启用时批量录入现有库存。独立期初单(QC 前缀)create 即过账(无草稿流):单事务内校验仓库存在/序列号仓拒收("期初不支持序列号物品,请走入库单")/批次·保质期仓必填批次号/物品存在/同单不重复/每物品×仓库限一次期初(命中"已有期初"整单回滚)→ 落期初单头/行(含单价/批次/效期/库位快照,head `total_qty`=Σ行)→ 服务端构造 `InboundCreateDTO`(refType=opening,refDocId=期初单 ID,docType="期初")调 `InboundService.create` 走现有入库链路(StockCoreService 零改动,库存/流水可追溯,任一行失败整单回滚)。列表/详情带 DataScope 数据权限;refType 中文映射加"期初"。

---

## 7. 目录结构

```
inventory-system/
├── AGENTS.md                  AI 编码代理工作手册(代理原生读取)
├── docker-compose.yml         PG 16-alpine,端口 5433
├── scripts/mvn.sh             Maven 包装脚本(本机 bash 路径兼容)
├── docs/迭代日志.md            迭代流水账(每次实质变更追加一条)
├── server-java/               Spring Boot 后端(阿里规范)
│   ├── pom.xml                Boot 3.5.x / MyBatis-Plus 3.5.x / jjwt / springdoc / EasyExcel 3.3.4(V12) / Lombok / checkstyle
│   ├── checkstyle.xml         阿里规范规则集(中文 Javadoc 适配)
│   ├── src/main/java/com/company/inventory/
│   │   ├── InventoryApplication.java  入口(@MapperScan mapper 包;JVM 时区锁 Asia/Shanghai)
│   │   ├── common/            错误码 / BizException / 全局异常 / 分页 / ApprovalGuard
│   │   ├── config/            MybatisPlus / Jwt / @RequireRole / Swagger / Jackson(东八区+格式)
│   │   ├── controller/        20 个 Controller
│   │   ├── model/             数据层:entity(DO)/dto/vo/query 四包,按功能再分包
│   │   ├── mapper/            31 个 Mapper + resources/mapper/*.xml
│   │   ├── service/ (+impl/)  业务层;StockCoreService = 库存核心
│   │   └── support/           DictReferenceRegistry(字典引用校验注册表)
│   ├── src/main/resources/
│   │   ├── application.yml    8081 / 5433 / JWT / jackson(Asia/Shanghai)
│   │   └── db/{schema,V2__phase1,V3__dict,V4__dict_type,V5__audit_fields,V6__snake_case,V7__rbac,V8__rbac2,V9__field_ext,V10__field_ext2,V11__return,V12__import_export,V13__opening_stock,seed}.sql
│   └── src/test/java/         24 个测试类,159 条(库存核心/并发/采购/销售/调拨/盘点/调整编辑/权限/字典/预警/仓库库位编辑/审计字段/日期解析/系统监控/RBAC 批 1a/RBAC 批 2 多角色+数据权限/V9 通用字段补全/V10 对标字段补齐/V11 退货/V12 导入导出/V13 期初)
└── web/                       Vite + React 18 + antd 5
    └── src/
        ├── api/  auth/  components/  layout/
        ├── pages/             31 个页面(25 个业务模块):login / dashboard / inbound(list+form) /
        │                      outbound(list+form) / stock / transaction / item(list+form) /
        │                      warehouse / location / user / role / menu / purchase(list+new) /
        │                      sales(list+new) / purchase-return(list+new) / sales-return(list+new) /
        │                      transfer / stocktake / adjust / alert /
        │                      supplier / customer / dict / monitor
        ├── main.tsx           入口:dayjs.locale("zh-cn")(日历中文)
        └── styles/  theme.ts  浅色底 + 深蓝主色
```

---

## 8. 常见问题

**Q: 出库报"库存不足"?**
A: 该仓库/物品/批次下余额不足,或序列号已 `out`,或销售预占占用了可用量。可用 `/api/v1/stock` 查询余额与预占。

**Q: 销售审批被驳回"仍缺 N"?**
A: 审批时做预占可行性校验,可用量(库存 - 预占)不够即驳回,防超卖。

**Q: 日期选择器/日历显示英文?**
A: 日历文案来自 dayjs 全局 locale,`main.tsx` 已显式 `dayjs.locale("zh-cn")`;新增页面无需再配。

**Q: 时间显示成 UTC 或 ISO 裸格式?**
A: 后端 `InventoryApplication.main()` 已锁 JVM 时区 + Jackson `Asia/Shanghai` + `yyyy-MM-dd HH:mm:ss`,新接口沿用即可。

**Q: PG 表名大小写报错 "relation does not exist"?**
A: 表/列统一小写蛇形(V6 迁移后),手写 SQL 不写引号;DO 走 `map-underscore-to-camel-case` 自动映射,新增 DO/XML 照抄 `StockMapper.xml` 的写法即可。

**Q: MyBatis-Plus 空集合 `IN ( )` 报 500?**
A: `selectByIds`/`IN` 前必须 `isEmpty` 防护(本项目 6 处已有)。

**Q: 端口?**
A: PG 5433(避开本机 5432);后端 8081;前端 dev 5173。
