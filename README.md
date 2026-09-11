# 库存管理系统

> 前后端分离项目(Java 21 + Spring Boot 3.5 + MyBatis-Plus + PostgreSQL 16 / Vite + React + antd)。
> 后端按阿里开发规范分层,出库条件 UPDATE 防穿仓、FEFO/FIFO 选批、序列号台账等核心规则零弱化。
> 12 条测试全绿(9 核心 + 1 并发穿仓 + 2 权限),阿里 checkstyle 规则集。

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

后端分层:`controller → service(/impl) → mapper → entity(DO)`,出入库单据头/行/流水/余额/序列号在单个事务内完成(`StockCoreService`),出库扣减为条件 `UPDATE ... WHERE quantity >= qty`,affected rows=0 即"库存不足"整单回滚。

> 说明:PG 库表名/列名为 PascalCase/camelCase(建库时带引号),实体 DO 通过
> `@TableName("\"X\"")` / `@TableField("\"col\"")` 显式写带引号标识符,保证 SQL 与 DDL 一致。

---

## 2. 启动步骤

```bash
# 1. 启动 PG(已起过可跳过)
docker compose up -d

# 2. 推 schema + 种子数据(已推过可跳过)
docker exec -i inventory-postgres psql -U inv -d inventory < server-java/src/main/resources/db/schema.sql
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
bash ../scripts/mvn.sh test        # 12 条,全绿,无 skip
bash ../scripts/mvn.sh package     # 0 错误
bash ../scripts/mvn.sh checkstyle:check
```

> 本机 bash 下 `mvn` 直接跑有 classworlds 路径 bug,构建一律走 `scripts/mvn.sh` 包装脚本。
> 默认种子数据:3 仓库(原材料/成品/五金) + 4 物品 + 库位 + 3 用户。

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
| 入库单 / 出库单 创建 | ✅ | ✅ | ❌ (后端 403) |
| 入库单 / 出库单 列表 | ✅ | ✅ | ✅ |
| 物品:增 / 查 | ✅ / 查 | 查 | 查 |
| 仓库 / 库位:增改 / 查 | ✅ / 查 | 查 | 查 |
| 用户管理 | ✅ | ❌ | ❌ |

后端用 `JwtInterceptor` + `@RequireRole` 注解:无 token 返回 `401 {statusCode,error,message}`,角色不够返回 `403`,message 为中文"无权限"。
前端 axios 拦截器:401 自动清 token 跳 `/login`,403 弹错误提示。

---

## 5. API 一览(全部中文描述,前缀 `/api/v1`)

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| POST | `/auth/login` | 登录,返回 token + user | 公开 |
| POST | `/auth/password` | 修改自己密码 | 登录 |
| GET  | `/auth/me` | 获取当前用户 | 登录 |
| GET  | `/dashboard/summary` | 首页统计 | 登录 |
| GET  | `/warehouses` `/warehouses/:id` | 仓库列表 / 详情 | 登录 |
| POST | `/warehouses` | 新建仓库 | admin |
| GET  | `/locations` | 库位列表(可按仓库筛) | 登录 |
| POST | `/locations` | 新建库位 | admin |
| GET  | `/items` | 物品列表(可按关键字筛) | 登录 |
| GET  | `/items/:id` | 物品详情 | 登录 |
| POST | `/items` | 新建物品 | admin |
| GET  | `/stock` | 库存余额(仓库/物品/批次筛) | 登录 |
| GET  | `/transactions` | 流水查询(分页) | 登录 |
| GET  | `/inbound` `/:id` | 入库单列表 / 详情 | 登录 |
| POST | `/inbound` | 新建入库单 | admin/operator |
| GET  | `/outbound` `/:id` | 出库单列表 / 详情 | 登录 |
| POST | `/outbound` | 新建出库单 | admin/operator |
| GET  | `/users` | 用户列表 | admin |
| POST | `/users` | 新建用户 | admin |
| PUT  | `/users/:id` | 编辑(角色/状态/重置密码) | admin |

列表统一返回 `{rows, total, page, pageSize}`,错误统一 `{statusCode, error, message}`。

---

## 6. 核心业务规则

1. **仓库 4 开关**:`enableBatch` / `enableExpiry` / `enableSerial` / `enableLocation` 决定入库/出库必填项,不依赖 `warehouse_type`。
2. **入库**:批次不存在自动创建;启用保质期必须带批次;序列号条数 = 入库数量(整数)。
3. **出库选批**:
   - 启用保质期 → **FEFO**(expiryDate 升序,NULL 最后)
   - 仅启用批次 → **FIFO**(按最近入库流水 createdAt)
   - 也可手动指定批次
4. **出库扣减**:条件 `UPDATE "Stock" SET quantity = quantity - n WHERE ... AND quantity >= n`,0 行抛"库存不足"整单回滚。**禁止先 SELECT 校验再 UPDATE**(防穿仓关键,见 `mapper/StockMapper.xml` 的 `deductStock`)。
5. **余额唯一键**: `(warehouse, item, batch, location)`,默认 `batch=0, location=0`。
6. **流水**:只插不改,必带 `afterQty`(变动后结存)。
7. **序列号台账**:独立表,出库逐号条件更新(仅 `in_stock` 且仓库匹配),任一失败整单回滚。
8. **整单事务**:单据头 + 单据行 + 流水 + 余额 + 序列号 同事务。
9. **单据号**: `RK-YYYYMMDD-NNNN` / `CK-YYYYMMDD-NNNN`,按天序列。

---

## 7. 目录结构

```
inventory-system/
├── docker-compose.yml         PG 16-alpine,端口 5433
├── scripts/mvn.sh             Maven 包装脚本(本机 bash 路径兼容)
├── server-java/               Spring Boot 后端(阿里规范)
│   ├── pom.xml                Boot 3.5.x / MyBatis-Plus 3.5.x / checkstyle
│   ├── checkstyle.xml         阿里规范规则集(35 条,中文 Javadoc 适配)
│   ├── src/main/java/com/company/inventory/
│   │   ├── InventoryApplication.java  入口(@MapperScan mapper 包)
│   │   ├── common/            错误码 / BizException / 全局异常 / 分页
│   │   ├── config/            MybatisPlus / JwtInterceptor / Swagger 等
│   │   ├── controller/        8 个 Controller
│   │   ├── dto/  entity/  vo/ 入参 / DO / 出参
│   │   ├── mapper/            12 个 Mapper + resources/mapper/*.xml
│   │   └── service/ (+impl/)  业务层;StockCoreService = 库存核心
│   ├── src/main/resources/
│   │   ├── application.yml    8081 / 5433 / JWT
│   │   └── db/{schema.sql,seed.sql}
│   └── src/test/java/         StockCore(9) + Concurrency(1) + Auth(2)
└── web/                       Vite + React + antd
    └── src/
        ├── api/http.ts        axios(baseURL=/api/v1)
        ├── auth/  layout/     登录态 / 角色菜单
        └── pages/             login / dashboard / inbound / outbound / stock /
                               transaction / item / warehouse / location / user
```

---

## 8. 常见问题

**Q: 出库报"库存不足"?**
A: 该仓库/物品/批次下余额不足,或序列号已 `out`。可用 `/api/v1/stock` 查询当前余额。

**Q: 启用保质期的仓库入库时必须填什么?**
A: 必须填批次号 + 保质期(或批次已存在时只填批次号即可)。

**Q: 启用序列号的仓库数量必须为整数?**
A: 是,序列号一条对应一台实物。

**Q: 启用了序列号但出库没填序列号?**
A: 后端 400 拒绝,整单回滚。

**Q: PG 表名大小写报错 "relation does not exist"?**
A: 表/列是建库时带引号的 PascalCase/camelCase,手写 SQL 必须带双引号;DO 已用引号注解,新增 DO/XML 照抄 `StockMapper.xml` 的写法。

**Q: 端口?**
A: PG 5433(避开本机 5432);后端 8081;前端 dev 5173。
