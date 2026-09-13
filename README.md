# 库存管理系统

> 前后端分离项目(Java 21 + Spring Boot 3.5 + MyBatis-Plus 3.5 + PostgreSQL 16 / Vite + React 18 + antd 5 + @ant-design/pro-components 2.8 列表页 ProTable、单据表单页 ProForm 表头)。
> 企业级进销存一期:采购/销售/调拨/盘点/调整/预警/审批/预占/退货/期初/报表 + 基础出入库,22 个 Controller,前端 34 个页面(27 个业务模块)。
> 后端按阿里开发规范分层,出库条件 UPDATE 防穿仓、FEFO/FIFO 选批、序列号台账等核心规则零弱化。
> **200 条测试全绿**(114 存量 + 15 RBAC 批 1a + 2 RBAC 批 2 + 4 V9 通用字段补全 + 5 V10 对标字段补齐 + 8 V11 退货 + 6 V12 导入导出 + 5 V13 期初 + 4 V15 报表中心 + 3 V16 操作日志 + 6 V17 单据明细行 + 12 V18 三单匹配/结算域 + 1 V19 盘点防重复生成 + 1 V19b 序列号仓盘点调整 + 11 V20 库存成本移动均价报表 + 3 V20 数据权限按 id 补 403),阿里 checkstyle 规则集(违规 0),前端 build 0 错。
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
bash ../scripts/mvn.sh test        # 200 条,全绿,无 skip
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
| 数据权限(列表行级过滤:批 2 7 列表 + V11 退货 2 + V13 期初 + V15 报表 4 + V17 仓维度 8 类明细行列表,口径与主表一致;V20 起调拨/盘点/调整 3 类单据按 id 的详情与写操作也校验,无权仓 403) | 豁免全量 | 仅授权仓 | 仅授权仓(未授权查空) |
| 系统监控 | ✅ | ❌ | ❌ |
| 操作日志(V16,写操作审计流水查询) | ✅ | ❌ | ❌ |
| 发票 建/改/确认/作废(V18) | ✅ | ✅ | ❌ |
| 付款单/收款单 建/作废(V18) | ✅ | ✅ | ❌ |
| 应收/应付台账与结算总览(V18,与列表读一致) | ✅ | ✅ | ✅ |
| 角色管理(RBAC:角色 CRUD + 角色-菜单分配) | ✅ | ❌ | ❌ |
| 菜单管理(RBAC:菜单树查看/增删改) | ✅ | ❌ | ❌ |

后端用 `JwtInterceptor` + `@RequireRole` 注解:无 token 返回 `401 {statusCode,error,message}`,角色不够返回 `403`,message 为中文。
RBAC(V7 起):JWT claim 由单 `role` 升级为 `roles` 数组(旧单值 token 兼容回退);`@RequireRole` 语义改为"用户角色集与注解有交集即通过";新增 `@RequirePermission("code")` 按钮级权限码注解(权限码 = 用户多角色 sys_role_menu 并集中 type='button' 的 menu_code);`GET /auth/menus` 返回当前用户并集菜单树(仅目录+菜单,附各菜单下按钮权限码)供前端动态导航(前端动态化在批 1b)。用户多角色(sys_user_role 并集),`sys_user.role` 列存量兼容保留(未绑定角色的存量用户登录回退读该列)。
RBAC 批 2(V8 起):用户 API 多角色化(`POST /users` 传 `roleIds` 必填;`PUT /users/:id` 传 `roleIds` 可选 + `warehouseIds` 可选,空列表 = 清空仓库授权);VO 新增 `roles`(编码+名称)与 `warehouseIds`,旧 `role` 字段保留取首角色。数据权限:`JwtInterceptor` 每请求写 `DataScope`(admin 豁免 null / 空 = 查空 / 非空 = 仅授权仓),库存/流水/入库/出库/盘点/调整/调拨 7 个列表接口按授权仓过滤(调拨为源仓或目的仓任一命中;仪表盘/预警暂不纳入);V20 起调拨/盘点/调整 3 类单据按 id 的入口(详情读 + 提交/审批/驳回/作废/录入实盘/刷新快照/生成调整单)也校验授权仓,无权仓 403 FORBIDDEN。V8 seed 补系统组按钮码(user/role/menu/dict 共 13 个)+「菜单管理」菜单(/menus,sort 在角色权限之后),全部绑 admin。
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
| GET | `/reports/stock-monthly` `/reports/stock-ageing` `/reports/purchase-recon` `/reports/sales-recon` | 报表中心 4 报表(进销存月报/库龄呆滞/采购对账/销售对账,只读聚合,分页) | 登录(菜单控可见性) |
| GET | `/reports/cost` `/reports/cost/export`(V20) | 库存成本(移动均价)报表:零建表按流水实时回放(成本单元=仓+物品+批次,加权入库/均价消耗/调拨成本随货走/盘盈亏按均价;日期可截止回放),不分页全量行+合计行,xlsx 导出 | 登录(菜单控可见性;数据权限与报表中心同口径) |
| GET | `/reports/{stock-monthly,stock-ageing,purchase-recon,sales-recon}/export` | 报表 xlsx 导出(与列表读一致,不分页) | 登录(菜单控可见性) |
| GET | `/operation-logs` | 操作日志列表(写操作执行流水,username/module/结果/日期区间筛选,时间倒序,分页) | admin |
| GET | `/operation-logs/modules` | 操作日志模块筛选项(LogModule 集中映射) | admin |
| GET | `{单据前缀}/lines`×10(V17) | 单据明细行拍平列表(采购/销售订单、入/出库、调拨、盘点、调整、期初、采/销退货各一个;行 = 单据行 JOIN 主表关键字段,支持物品关键字/批次号筛选,全量分页) | 登录(仓维度单据按数据权限过滤,口径同主表) |
| POST/PUT/POST×2/GET | `/invoices` `/invoices/:id`(V18) | 发票:新建(含行,防超开校验)/更新草稿/确认(draft→confirmed)/作废/详情/列表(类型/对方/日期/关键字/状态筛选,全量分页) | 写 admin+operator,读登录(列表与采购/销售订单同口径不加 DataScope) |
| GET | `/invoices/invoiceable-lines`(V18) | 可挂票行(源单 approved/completed/closed 行,已开票量=非作废票行 Σ,剩余可开=含税额−已开票,行唯一约束 (src,sign) 防重挂) | 登录 |
| POST/POST/GET×3 | `/payments` `/payments/:id`(V18) | 付款/收款:新建(payType payment/receipt,勾 confirmed 正票核销,行级防超核,负票禁核)/作废(释放核销额度)/详情/列表 | 写 admin+operator,读登录(同口径) |
| GET | `/payments/unsettled-invoices`(V18) | 可核销票(confirmed 正票,剩余=净额−已核销>0) | 登录 |
| GET | `/settlement/ap` `/settlement/ar`(V18) | 应付/应收台账(对方维度实时聚合零建表:累计单据金额/退货冲减/发票净额(仅 confirmed)/已核销/余额;行展开:① 对方全部发票(含未确认/作废,含正票已核销)② 订单执行子表(未执行/执行中/已完成,采购退货扣减净到货量);分页) | 登录(采购单无仓字段不加 DataScope,销售侧按自有仓库口径待后续对齐) |
| GET | `/settlement/dashboard`(V18) | 结算总览(应付/应收余额合计,仪表盘 2 卡) | 登录 |

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
20. **期初库存(V13)**:系统启用时批量录入现有库存。独立期初单(QC 前缀)create 即过账(无草稿流):单事务内校验仓库存在/序列号仓拒收(“期初不支持序列号物品,请走入库单”)/批次·保质期仓必填批次号/物品存在/同单不重复/每物品×仓库限一次期初(命中“已有期初”整单回滚)→ 落期初单头/行(含单价/批次/效期/库位快照,head `total_qty`=Σ行)→ 服务端构造 `InboundCreateDTO`(refType=opening,refDocId=期初单 ID,docType="期初")调 `InboundService.create` 走现有入库链路(StockCoreService 零改动,库存/流水可追溯,任一行失败整单回滚)。列表/详情带 DataScope 数据权限;refType 中文映射加“期初”。
21. **单据打印(V14,纯前端,后端零改动)**:10 类单据(采购订单/销售订单/入库单/出库单/调拨单/采购退货单/销售退货单/盘点单/库存调整/期初库存)可打印纸质归档。各页详情弹窗底部新增「打印」按钮 → 通用组件 `components/PrintDocModal.tsx` 渲染 A4 黑白朴素版式(标题+单号居中、头部 label/value 网格空值自动过滤、原生 `<table>` 明细表 1px 黑边框带合计行、底部状态/备注/制单审批签字区),点「打印」调浏览器原生 `window.print()`;打印 `@media print` 规则在 `styles/global.css`(隐藏主应用与其它弹窗,仅保留 `.print-doc-sheet`,`@page` A4 边距 12mm)。每页用自己的 `buildPrintData(detail)` 转打印数据(金额 toFixed(2)、日期 yyyy-MM-dd、退货带原单号、出入库带仓库/承运/车牌);权限与导出一致(列表能看即可打印),零新增依赖。
22. **报表中心(V15,只读聚合,StockCoreService/既有 mapper XML 零改动)**:单页 4 Tab(`ReportController` `/api/v1/reports` + `ReportMapper.xml` 报表专用 SQL 放 `mapper/report/` 子包):① **进销存月报**(item 维度跨仓汇总,筛仓库/物品/日期区间):期初量 = 期初前最后一次流水的 `after_qty`、期末量 = 期末后最后一次流水的 `after_qty`,均按库存粒度 (item,warehouse,batch,location) 窗口函数 `row_number() over (partition by 4 列 order by created_at desc)` 取 `rn=1` 后 Σ(某粒度组合区间前无流水贡献 0,不整行丢弃);入 = Σ change_qty (inbound/transfer_in/adjust_in)、出 = -Σ change_qty (outbound/transfer_out/adjust_out),`pre_alloc` 不计;期初+入-出=期末(测试断言);金额 = 区间内 inbound/outbound_doc(doc_date 在区间,finished)行表 Σ tax_inclusive_total,经流水 doc_no 关联存在性过滤(无快照为空);行展开各仓期末明细 ② **库龄/呆滞**(批次维度;无批次仓按 item+仓库 汇总,生产时间取该仓最早入方向流水日期):库龄区间 0-30/31-90/91-180/>180 文字不带颜色,呆滞 = 该物品+仓库 N 天(默认 90)内无出方向流水且当前量 > 0,文字「呆滞」 ③ **采购对账**(供应商维度:期间内采购单数/量/价税合计 + 退货量/金额 + 净采购,行展开期间内采购单+退货单明细;采购单无仓字段,数据权限经其关联入库单仓库过滤) ④ **销售对账**(客户维度,销售单/退货单按自有仓库过滤)。4 报表各带 xlsx 导出(复用 `ExcelSupport`/`ExportButton` 机制,与列表读一致不加权限码,菜单权限控页面可见性);数据权限与 7 列表同口径(admin 豁免/未授权查空/授权仓过滤);Service 层纯 SELECT(只读),列表分页全在 SQL 层(count + LIMIT/OFFSET) ⑤ **库存成本(移动均价,V20)**:零建表按 `stock_transaction` 实时回放(见核心规则 31)。
23. **操作日志(V16,写操作审计流水)**:记录全部写操作(POST/PUT/DELETE)的执行流水,供 admin 审计“谁在何时对什么做了什么、成败与否”。`OperationLogAspect` 环绕切面(pom 新增 spring-boot-starter-aop):切点为 Controller 包下带 POST/PUT/DELETE 映射注解的方法(GET 不记),登录/登出排除(登录失败风暴灌爆日志);落 `operation_log` 表(主键 BIGSERIAL,纯日志表;索引 username/created_at/(module,created_at))字段:username(取 `UserContext.get()`,空兜底 anonymous)/ip(X-Forwarded-For 首段或 remoteAddr)/module(由 `common/constant/LogModule` 集中映射 Controller 类简名→中文,未映射原样落)/action(POST/PUT/DELETE)/path/target_id(PathPattern 匹配 @RequestMapping 模板从路径变量取数字 ID,对象类型落模块名,**切面零业务查库**——对象单号拿不到就不落,严禁反查)/success(1/0)/error_msg(BizException 取其 message 截断 500,其他异常记“系统异常”)/cost_ms(nanoTime 计时)。**落库失败绝不抛出**:insert 包 try-catch 只 log.warn,日志功能故障不影响业务主流程。查询:`OperationLogController` `/api/v1/operation-logs` 仅 GET(列表 username 模糊/module 精确/结果/日期区间(DateRangeSupport)筛选 + 分页时间倒序;`/modules` 返回模块筛选项),类级 `@RequireRole("admin")`;菜单 seed 系统分组下 operation-logs 仅绑 admin。定时清理:`@EnableScheduling` + `@Scheduled(cron = "0 0 3 * * ?")` 每天 3 点删 `operation-log.retention-days`(application.yml 默认 180)前的记录,清理失败只 log.warn。前端:OperationLogPage(ProTable 规范:无横幅/span6;列 时间/用户/模块/操作 Tag/请求路径/对象/结果/耗时/IP,失败行可展开看异常消息,筛选 用户/模块/结果/日期区间)。注意:403 越权与参数绑定 400 在拦截器/参数解析阶段发生,未进入 Controller 方法,切面不记(业务校验失败 BizException 记 success=0)。
24. **单据明细行列表(V17,主表/明细切换,纯只读)**:10 类带明细单据的列表页各加 `GET {前缀}/lines`(采购/销售订单、入/出库、调拨、盘点、调整、期初、采/销退货):行 = 单据行 JOIN 主表(docNo/docDate/status)+ 物品(itemCode/itemName/spec/unit,LEFT JOIN item)+ 对方单位(供应商/客户/仓库,LEFT JOIN 防孤儿),每行带 docId 供前端点开详情弹窗;额外支持 itemKeyword(物品编码或名称模糊)/batchNo(精确,仅入库/期初行有批次列)。排序 doc_date DESC, doc_no DESC, line_no;全量分页契约 `{rows,total,page,pageSize}`。后端:`DocLineService/Impl` 门面(注入 10 个单据主表 Mapper,统一方法 `selectDocLines`,XML 跟随主表名 `resources/mapper/*Mapper.xml`,显式列禁 select *,数据库层分页);数据权限与主表同口径(仓维度 8 类走 `DataScope` 授权仓过滤,调拨为源仓或目的仓命中;采购/销售订单与主表一致不过滤)。前端:10 个列表页筛选区上方 Segmented「主表/明细」(默认主表,组件内状态),明细视图换 columns 与 /lines 请求,共享原筛选区(日期/单号/状态/对方单位),物品/批次号筛选项仅明细视图渲染(span6 规范不变);明细行单据号可点,拉整单详情复用现有详情弹窗。零业务逻辑改动:纯只读查询,不碰过账/扣减/状态机,StockCoreService 与 StockMapper.xml 零改动。
25. **三单匹配/应收应付(V18,结算域,对标用友 U8/金蝶/SAP,只做业务台账不做财务凭证)**:① 4 新表(invoice/invoice_item/payment_doc/payment_line,V18__settlement.sql 幂等):发票头(FP 前缀,类型 purchase/sales,对方 供应商/客户,三态 draft/mismatch/confirmed/voided,行净额合计=头总额)+ 发票行(src 单据三元组+sign 正负向唯一约束,sign 冗余头表);付款/收款单(FK/SK 前缀,payType payment/receipt,created/voided)+ 核销行(invoice_id 唯一防重核销)。② 发票生命周期:仅 draft 可改(重算总额,金额不匹配→mismatch,平账→draft 可确认);confirm 要求平账且行净合计=头额;void 整单作废释放唯一约束(物理删行释放 (src,sign) 占用);防超开:源行已有未作废票占着→拒,开票额>含税额→拒;退货单过账后自动生成红字负票 draft(与正票同 src 不同 sign 并存,金额取负)。③ 付款/收款:勾 confirmed 正票核销,行级防超核(核销额>剩余可核→拒),负票禁核,作废释放核销额度;方向强校验(payment 只能付供应商票/receipt 只能收客户票)。④ 台账零建表实时聚合:应付(采购行 Σ 含税−采购退货行 Σ 含税)/应收(销售−销售退货),发票净额仅计 confirmed,已核销计未作废付款单,余额=单据−发票±核销;行展开对方全部发票(含未确认/作废)+ 订单执行子表(订单级未执行/执行中/已完成);仪表盘 2 卡(应付红/应收绿)。⑤ 权限:写接口 invoices:edit/payments:create/receipts:create(admin+operator),读与列表一致不加权限码,菜单控可见性;菜单 seed 结算分组 5 菜单 8 按钮。⑥ 数据权限:发票/付款/台账/仪表盘均不加 DataScope(与采购/销售订单列表同口径,采购单无仓字段;销售侧仓库口径待后续对齐)。StockCoreService/StockMapper.xml/原 172 测试零改动。
26. **SPA 共享组件路由复用修复(付款/收款/台账)**:同一组件以不同 `mode` 挂两条路由(`PaymentListPage` payment/receipt、`LedgerPage` ap/ar、`PaymentNewPage` payment/receipt)时,React Router 复用同一组件实例,侧栏切换只更新 props 不重挂载,列表不刷新、下拉数据滞留上一模式(收款页曾按付款口径请求 500)。修复:6 条路由元素加 `key`(payment/receipt、ap/ar、payment-new/receipt-new),模式切换强制重挂载,ProTable 重新请求、筛选区与下拉按当前模式重建。
27. **盘点单防重复生成调整单(库存完整性)**:已审批盘点单可无限点击"生成调整单",调整单每执行一次动一次库存,重复生成 = 盘盈/盘亏被重复计入。三层防护:① 服务层 `generateAdjust` 前置校验——该盘点单(`refDocNo` 匹配)已有未作废调整单 → 400"已生成过调整单",全部作废后方可重新生成;② 部分唯一索引 `uk_adjust_ref_doc_type (ref_doc_no, adjust_type) WHERE ref_doc_no IS NOT NULL AND status <> 'voided'` 并发竞态兜底(同盘点单盘盈/盘亏各一张,手工调整单 ref 为 null 不受限),全局异常处理器对 `DataIntegrityViolationException` 统一转 400"操作冲突,请刷新后重试";③ 前端列表/详情 VO 带 `adjustGenerated` 标记(批量一次查询非 N+1),已生成单"生成调整单"按钮替换为"已生成"Tag。来源单号仅由服务端写入:手工建/改调整单 API 忽略客户端传入的 refDocNo(防手工单占用盘点单索引位),盘点生成走独立方法 `createWithRef`。
28. **序列号仓库盘点差异调整(V19b,过账补序列号)**:盘点录入只录数量不录序列号,而启用序列号的仓库(如成品仓)过账要求逐号,导致盘点生成的调整单审批必 400("入库行必须填写序列号")。修复在调整执行层(`StockAdjustServiceImpl.doExecute`,不碰 StockCoreService 红线):盘盈(gain)自动生成台账序列号,格式 `物品编码-ADJ-调整单号-3位序号`(如 RAW-RESIN-ADJ-TZ-20260913-0007-001,生成前查重,冲突 400 可作废重试);盘亏(loss/scrap)按本仓本物品 in_stock 台账按入库序取前 N 个,台账不足 → 400"实盘数量与台账不符,请作废后重新盘点"(不静默扣,台账不足本身说明盘点数据有误)。非序列号仓行为零变化。
29. **交互减确认(去掉冗余确认弹层)**:确认弹层只保留在不可逆/动库存操作(作废、关闭、调整单审批执行、菜单/角色删除),可回退或失败自动回滚的操作去掉弹层直接执行:盘点"生成调整单"(纯草稿,V19 已有防重复)、销售"审批"(纯预占,库存不足自动回退草稿)、调拨"审批执行"(事务回滚)、字典类型"停用/启用"(可再切换)。采购"审批"本就直接执行。判定标准:点错能否无副作用恢复——能则免确认。
30. **数据权限按单据 id 补 403(V20)**:批 2 的数据权限只做了列表行级过滤,调拨/盘点/调整 3 类单据按 id 的入口(`requireDoc`:详情读 + 提交/审批/驳回/作废/录入实盘/刷新快照/生成调整单)均裸 `selectById`,非 admin 可直接读/操作无权仓单据(operator 实测 GET /transfers/{id} 原 200)。修复:3 个 service 各加 `assertWarehouseAccess`(DataScope null=豁免放行;调拨源仓或目的仓任一命中放行;否则 `BizException.forbidden("无权操作该仓库的单据")` 403),在 `requireDoc` 取到单据后一处覆盖全部按 id 入口,`get` 详情也改走 `requireDoc` 统一口径。列表逻辑与采购/销售等其他 service 不动(口径独立项)。
31. **库存成本移动均价报表(V20,零建表实时回放,纯查询侧不碰过账链路)**:成本单元 = 仓库+物品+批次(批次 0/null 也独立成单元)。按 `created_at`(同时间戳按 id)升序回放该范围内全部流水:opening/inbound → 金额 += 数量×单据行单价(`inbound_doc_item.unitPrice`,docNo 兜底 `opening_stock_doc_item`),均价 = 金额/数量(缺行单价的入库流水金额按 0 计入不静默跳过);outbound/transfer_out/adjust_out(盘亏)→ 金额 -= 数量×当前均价(均价不变,销售行售价不参与);transfer_in → 金额 += 数量×源仓当时均价(成本随货走,同单号先出后入,源仓单元必须参与回放故回放不按仓过滤);adjust_in(盘盈)→ 金额 += 数量×当前均价;pre_alloc 等辅助流水不影响金额。数量一律以流水 `afterQty` 为权威(同库位后写覆盖,单元数量=各库位余额之和)。均价内部 scale 8 HALF_UP,对外展示 4 位(2.625 这类均价 2 位会失真),金额 2 位。`GET /reports/cost`(参数 warehouseId/itemId/date 均可空,date 回放截止当日 23:59:59,默认当前)+ `/reports/cost/export`(EasyExcel 同风格);不分页全量行(含数量为 0 的历史单元)+ 合计行;数据权限与报表中心同口径(admin 豁免/未授权查空/授权仓过滤输出行)。前端报表中心第 5 Tab「库存成本」(ProTable:仓库/物品/批次/数量/移动均价/成本金额,筛仓库/物品/日期默认今天,工具栏导出)。StockCoreService/StockMapper.xml 红线零改动。
32. **全面体检修复(缺参 500→400 + favicon)**:① 缺必填 @RequestParam 原落 500 兜底(/dicts/all、/invoices/invoiceable-lines、/payments/unsettled-invoices 等全局),GlobalExceptionHandler 补 MissingServletRequestParameterException → 400「缺少必填参数 X」;② index.html 加内联 SVG favicon(🐴),消除每页控制台 /favicon.ico 404 红叉。体检结论:库存↔流水逐单元对平、14 表零孤儿、124 端点三角色巡检零异常、31 页 JS 零报错。
33. **新建页明细行删除按钮(V21,修可编辑表操作列不渲染)**:采购订单/期初库存新建页用 EditableProTable 自动 option 列(actionRender 返回 dom.delete),scroll 布局下操作列表头有列但 body 不渲染 td,删除按钮缺失——点"添加行"多次后空行删不掉,卡死"请完善明细必填项"。改显式操作列(editable:false + removeLine,对齐入库/销售页既有写法),移除失效 actionRender。浏览器实测:加 3 行删空行成功,采购单 新建→提交→审批 全链路跑通。

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
│   │   ├── controller/        21 个 Controller
│   │   ├── model/             数据层:entity(DO)/dto/vo/query 四包,按功能再分包
│   │   ├── mapper/            32 个 Mapper + resources/mapper/*.xml
│   │   ├── service/ (+impl/)  业务层;StockCoreService = 库存核心
│   │   └── support/           DictReferenceRegistry(字典引用校验注册表)
│   ├── src/main/resources/
│   │   ├── application.yml    8081 / 5433 / JWT / jackson(Asia/Shanghai)
│   │   └── db/{schema,V2__phase1,V3__dict,V4__dict_type,V5__audit_fields,V6__snake_case,V7__rbac,V8__rbac2,V9__field_ext,V10__field_ext2,V11__return,V12__import_export,V13__opening_stock,V15__report_menu,V16__operation_log,seed}.sql
│   └── src/test/java/         29 个测试类,200 条(库存核心/并发/采购/销售/调拨/盘点/调整编辑/权限/字典/预警/仓库库位编辑/审计字段/日期解析/系统监控/RBAC 批 1a/RBAC 批 2 多角色+数据权限/V9 通用字段补全/V10 对标字段补齐/V11 退货/V12 导入导出/V13 期初/V15 报表中心/V16 操作日志/V17 单据明细行/V18 三单匹配+结算域/V19 盘点防重复生成/V19b 序列号仓盘点调整/V20 库存成本报表)
└── web/                       Vite + React 18 + antd 5
    └── src/
        ├── api/  auth/  components/  layout/
        ├── pages/             32 个页面(26 个业务模块):login / dashboard / inbound(list+form) /
        │                      outbound(list+form) / stock / transaction / item(list+form) /
        │                      warehouse / location / user / role / menu / purchase(list+new) /
        │                      sales(list+new) / purchase-return(list+new) / sales-return(list+new) /
        │                      transfer / stocktake / adjust / alert /
        │                      supplier / customer / dict / monitor / operation-log(V16)
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
