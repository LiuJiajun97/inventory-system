# 库存管理系统

> 前后端分离项目(Java 21 + Spring Boot 3.5 + MyBatis-Plus 3.5 + PostgreSQL 16 / Vite + React 18 + antd 5)。
> 企业级进销存一期:采购/销售/调拨/盘点/调整/预警/审批/预占 + 基础出入库,17 个 Controller,前端 24 个页面(20 个业务模块)。
> 后端按阿里开发规范分层,出库条件 UPDATE 防穿仓、FEFO/FIFO 选批、序列号台账等核心规则零弱化。
> **87 条测试全绿**,阿里 checkstyle 规则集(违规 0),前端 build 0 错。
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

后端分层:`controller → service(/impl) → mapper → entity(DO)`,层内按功能分文件夹(`dto/purchase/`、`entity/sales/` 等)。
出入库/采购/销售/调拨在单个事务内完成(`StockCoreService`),出库扣减为条件 `UPDATE ... WHERE quantity >= qty`,affected rows=0 即"库存不足"整单回滚。

> 说明:PG 库表名/列名为 PascalCase/camelCase(建库时带引号),实体 DO 通过
> `@TableName("\"X\"")` / `@TableField("\"col\"")` 显式写带引号标识符,保证 SQL 与 DDL 一致。

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
bash ../scripts/mvn.sh test        # 87 条,全绿,无 skip
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
| 单据审批 / 驳回(采购、销售、调拨、盘点、调整) | ✅(可自批) | ✅(禁自批) | ❌ |
| 低库存 / 临期预警 查询 | ✅ | ✅ | ✅ |
| 字典:读 | ✅ | ✅ | ✅ |
| 字典:增删改/停用 | ✅ | ❌ | ❌ |
| 用户管理 | ✅ | ❌ | ❌ |

后端用 `JwtInterceptor` + `@RequireRole` 注解:无 token 返回 `401 {statusCode,error,message}`,角色不够返回 `403`,message 为中文。
审批资格由 `common/support/ApprovalGuard` 统一裁决:**admin 可审批自己提交的单据,operator 禁自批**(防"提交-审批"死锁)。
前端 axios 拦截器:401 自动清 token 跳 `/login`,403 弹错误提示。

---

## 5. API 一览(前缀 `/api/v1`,全中文描述,列表统一 `{rows,total,page,pageSize}`)

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| POST | `/auth/login` `/auth/password` `/auth/me` | 登录 / 改密 / 当前用户 | 公开 / 登录 |
| GET | `/dashboard/summary` | 首页统计 | 登录 |
| GET/POST/PUT | `/warehouses` `/warehouses/:id` | 仓库列表 / 新建 / 编辑(编码不可改,含防不一致校验) | 登录 / admin |
| GET/POST/PUT | `/locations` `/locations/:id` | 库位列表 / 新建 / 编辑(编码与所属仓库不可改) | 登录 / admin |
| GET/POST/PUT | `/items` `/items/:id` | 物品列表 / 新建 / 编辑(编码不可改) | 登录 / admin |
| GET | `/stock` `/stock/batches/:id` | 库存余额 / 批次明细 | 登录 |
| GET | `/transactions` | 流水查询 | 登录 |
| GET/POST | `/inbound` | 手工入库单 | 登录 / admin+operator |
| GET/POST | `/outbound` | 手工出库单 | 登录 / admin+operator |
| GET/POST/PUT | `/suppliers` `/customers` | 供应商 / 客户 | 登录 / admin |
| GET/POST | `/purchase-orders` `/purchase-orders/:id/...` | 采购订单 + 提交/审批/驳回/到货 | 登录 / admin+operator |
| GET/POST | `/sales-orders` `/sales-orders/:id/...` | 销售订单 + 提交/审批/驳回/发货 | 登录 / admin+operator |
| GET/POST | `/transfers` | 调拨单(原子一步:出+入同事务) | 登录 / admin+operator |
| GET/POST | `/stocktakes` `/stocktakes/:id/...` | 盘点单 + 录入实盘/审批 | 登录 / admin+operator |
| GET/POST | `/stock-adjusts` | 库存调整单(盘盈亏等,含审批) | 登录 / admin+operator |
| GET | `/alerts/low-stock` `/alerts/expiry` | 低库存 / 临期预警 | 登录 |
| GET | `/dicts` `/dicts/all` | 字典查询(登录 / admin) | 登录 / admin |
| GET/POST/PUT | `/dicts/types` `/dicts/types/:typeCode` | 字典类型列表/新建/编辑(admin 可写) | 登录 / admin |
| POST/PUT/DELETE | `/dicts/admin...` | 字典项增删改/停用(引用校验) | admin |
| GET/POST/PUT | `/users` | 用户管理 | admin |

完整契约以 Swagger 为准:`http://127.0.0.1:8081/docs`。

---

## 6. 核心业务规则

**基础库存(一期前)**
1. **仓库 4 开关**:`enableBatch` / `enableExpiry` / `enableSerial` / `enableLocation` 决定入库/出库必填项,不依赖 `warehouse_type`。
2. **入库**:批次不存在自动创建;启用保质期必须带批次;序列号条数 = 入库数量(整数)。
3. **出库选批**:启用保质期 → **FEFO**(expiryDate 升序,NULL 最后);仅启用批次 → **FIFO**;也可手动指定。
4. **出库扣减**:条件 `UPDATE "Stock" SET quantity = quantity - n WHERE ... AND quantity >= n`,0 行抛"库存不足"整单回滚。**禁止先 SELECT 校验再 UPDATE**(`mapper/StockMapper.xml` 的 `deductStock`,核心逻辑禁止弱化)。
5. **余额唯一键**:`(warehouse, item, batch, location)`,默认 `batch=0, location=0`。
6. **流水**:只插不改,必带 `afterQty`(变动后结存)。
7. **序列号台账**:独立表,出库逐号条件更新,任一失败整单回滚。
8. **整单事务**:单据头 + 行 + 流水 + 余额 + 序列号 同事务。
9. **单据号**:`RK/CK/CG/XS/DB/PD/TZ-YYYYMMDD-NNNN`,按天序列(`DocNoService`)。

**进销存一期**
10. **价税分离**:订单明细 `unitPrice`(不含税)/`taxRate`/`taxAmount`/`totalAmount`(含税),合计 `sum = Σ totalAmount`。
11. **审批流**:单据 `draft → submitted → approved/rejected`,仅 approved 可执行到货/发货;审批资格见 §4(ApprovalGuard)。
12. **采购到货**:可部分到货累计,到齐自动 `completed`;**超收拒绝**(400)。
13. **销售预占**:审批即预占(`Stock.preAllocatedQty` 原子增加),发货时预占与库存同事务扣减,预占不清零则发货失败;审批时做预占可行性校验,不可行直接驳回(如"仍缺 N")。
14. **调拨**:源仓扣减 + 目的仓入库单事务内原子完成,任一失败整体回滚。
15. **盘点/调整**:盘点录入实盘差异 → 生成调整单 → 审批后过账,流水 type 为 `adjust_in/adjust_out`。
16. **预警**:低库存(`minStock` 阈值)与临期(效期 N 天内)只读查询,不自动改库存。
17. **字典**:配置类枚举(仓库类型/物品分类/结算方式)进字典表,类型可动态管理(DictType 表);状态机枚举(单据状态等)不进字典。停用被业务表引用的字典项被拒(400);停用含启用项的类型被拒(400);引用校验通过 `DictReferenceRegistry` 注册表统一管理。

---

## 7. 目录结构

```
inventory-system/
├── AGENTS.md                  AI 编码代理工作手册(代理原生读取)
├── docker-compose.yml         PG 16-alpine,端口 5433
├── scripts/mvn.sh             Maven 包装脚本(本机 bash 路径兼容)
├── docs/迭代日志.md            迭代流水账(每次实质变更追加一条)
├── server-java/               Spring Boot 后端(阿里规范)
│   ├── pom.xml                Boot 3.5.x / MyBatis-Plus 3.5.x / jjwt / springdoc / checkstyle
│   ├── checkstyle.xml         阿里规范规则集(中文 Javadoc 适配)
│   ├── src/main/java/com/company/inventory/
│   │   ├── InventoryApplication.java  入口(@MapperScan mapper 包;JVM 时区锁 Asia/Shanghai)
│   │   ├── common/            错误码 / BizException / 全局异常 / 分页 / ApprovalGuard
│   │   ├── config/            MybatisPlus / Jwt / @RequireRole / Swagger / Jackson(东八区+格式)
│   │   ├── controller/        17 个 Controller
│   │   ├── dto/  entity/  vo/  query/  层内按功能分包
│   │   ├── mapper/            27 个 Mapper + resources/mapper/*.xml
│   │   ├── service/ (+impl/)  业务层;StockCoreService = 库存核心
│   │   └── support/           DictReferenceRegistry(字典引用校验注册表)
│   ├── src/main/resources/
│   │   ├── application.yml    8081 / 5433 / JWT / jackson(Asia/Shanghai)
│   │   └── db/{schema,V2__phase1,V3__dict,V4__dict_type,seed}.sql
│   └── src/test/java/         13 个测试类,87 条(库存核心/并发/采购/销售/调拨/盘点/权限/字典/预警/仓库库位编辑)
└── web/                       Vite + React 18 + antd 5
    └── src/
        ├── api/  auth/  components/  layout/
        ├── pages/             24 个页面(20 个业务模块):login / dashboard / inbound(list+form) /
        │                      outbound(list+form) / stock / transaction / item(list+form) /
        │                      warehouse / location / user / purchase(list+new) / sales(list+new) /
        │                      transfer / stocktake / adjust / alert / supplier / customer / dict
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
A: 表/列是建库时带引号的 PascalCase/camelCase,手写 SQL 必须带双引号;DO 已用引号注解,新增 DO/XML 照抄 `StockMapper.xml` 的写法。

**Q: MyBatis-Plus 空集合 `IN ( )` 报 500?**
A: `selectByIds`/`IN` 前必须 `isEmpty` 防护(本项目 6 处已有)。

**Q: 端口?**
A: PG 5433(避开本机 5432);后端 8081;前端 dev 5173。
