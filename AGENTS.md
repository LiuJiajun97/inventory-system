# AGENTS.md

本文件给 AI 编码代理(Claude Code / Codex / pi / opencode 等)提供项目上下文。
人看的文档:README.md(总览)、docs/迭代日志.md(变更流水)、docs/权限矩阵.md、docs/API一览.md、docs/核心业务规则.md。

## 项目

企业级进销存(采购/销售/调拨/盘点/调整/预警/审批/预占),单机轻量部署。
后端 `server-java/`:Java 21 + Spring Boot 3.5 + MyBatis-Plus 3.5 + PostgreSQL 16。
前端 `web/`:Vite + React 18 + antd 5。阿里开发规范,注释与 UI 文案全中文。

## 常用命令

建议用 IDE 的运行配置启动前后端(见 `.idea/runConfigurations/` 与下文),避免命令行另起实例撞端口。

```bash
# 构建/测试(必须在 server-java/ 下跑,Maven 必须经包装脚本,根目录直接跑报 no POM)
bash ../scripts/mvn.sh test               # 后端测试(当前 291 条,必须全绿)
bash ../scripts/mvn.sh checkstyle:check   # checkstyle(必须 0 违规)
bash ../scripts/mvn.sh package            # 构建
bash ../scripts/mvn.sh spring-boot:run    # 启动后端(8888)

# 前端(web 下)
npm run dev                            # dev(5173,代理 /api → 8888)
npm run build                          # 构建(必须 0 错)

# 数据库(docker 容器 inventory-postgres,端口 5433,库 inventory,用户 inv)
docker compose up -d                   # 起 PG
# 新库初始化只需这两个文件(V2~V20 为历史迁移归档,新库勿执行;存量库改结构手工写 ALTER 追加到最新 V 文件):
docker exec -i inventory-postgres psql -U inv -d inventory < server-java/src/main/resources/db/schema.sql
docker exec -i inventory-postgres psql -U inv -d inventory < server-java/src/main/resources/db/seed.sql
```

完工前必须跑:三件套(test/checkstyle/package)+ `npm run build`,数字写实测值。

## 运行环境

- 后端 8888、前端 5173,**禁止同时多实例**(会撞端口)。
  前端只改 web 代码时靠 HMR,无需重启;**后端 Java 改动必须重启后端才生效**,标准流程:
  1) 按端口 8888 找到 java 进程;2) 结束进程;3) 用 IDE 运行配置(InventoryApplication)或 `bash scripts/mvn.sh spring-boot:run` 重启
- PG:容器 `inventory-postgres`,端口 5433,库 `inventory`(开发)/`inventory_test`(测试)
- Redis:容器 `inventory-redis`,端口 6379(`docker compose up -d` 同 PG 一起起),存幂等/登出黑名单/登录锁定状态与权限缓存(host/port 可用 `REDIS_HOST`/`REDIS_PORT` 环境变量覆盖)
- 账号:admin/admin123、zhangsan/zhang123(operator)、lisi/lisi123(viewer)

## 架构约定

- 后端分层:`controller → service(/impl) → mapper → model(数据层:entity(DO)/dto/vo/query 四包,按功能再分包)`
- 列表接口统一:Query DTO 绑定(@Valid)+ 全量分页,返回 `{rows,total,page,pageSize}`;错误体 `{statusCode,error,message}`
- 时间:东八区(JVM 已锁 Asia/Shanghai),接口格式 `yyyy-MM-dd HH:mm:ss`,日期 `yyyy-MM-dd`
- 权限:写接口加 `@RequireRole("admin")`;审批动作必须走 `common/support/ApprovalGuard`(admin 可自批、operator 禁自批),不要手写角色判断
- 字典:配置类枚举才进字典表;状态机/流水枚举不进,前端静态映射(`web/src/components/StatusTag.tsx` 的 BIZ_LABEL,新增 bizCode 同步加,与 `ErrorCode.BIZ_CODE_*` 对齐);业务表引用字典列的停用校验在 `DictReferenceRegistry`,新表要加注册

## 前端约定(web/,违反即返工)

- 布局:列表页**无标题横幅**(侧栏已表明当前页);页面级操作按钮(如"新建")放筛选区同一行右侧(ProTable `search.optionRender`),不单独占行;筛选区 4 列/行(`search.span: 6`)
- 按钮:新建按钮文案只写"新建"两个字
- 列表:行高统一 **45px**(`theme.ts` 的 cellPaddingBlock=11);金额/数量一律走 `fmtMoney`/`fmtQty`(`web/src/utils/format.ts`),禁直接 `toFixed`;空态用 `EmptyHint`
- 单据新增界面(采购/销售/调拨/调整 4 页统一):页面头部 + 全宽卡片 + 底部固定操作条;添加行按钮在明细表下方左对齐,主按钮(提交)底部居中
- 详情:弹窗统一宽度 960、行紧凑;10 个详情抽屉样式统一
- 时间:前端传**空格分隔**的东八区字符串(如 `2026-09-01 00:00:00`),后端统一入口 `DateRangeSupport`;展示格式 `yyyy-MM-dd HH:mm:ss`
- 主题:颜色/圆角一律用 `web/src/theme.ts` 设计 token(主色 #3056d3、圆角 10),不随手写 hex
- 路由:`withSuspense` 懒加载(每页自带 Suspense);react-router v6 **禁止 `<Suspense>` 出现在 `<Route>` 子树**(首屏白屏,build 不报错)
- 仓库范围切换:走 `WarehouseScopeContext`;列表页要配 `useEffect + actionRef.reload()`(Context 变化 ProTable 不会自动重请求)
- 列表状态筛选:`useSearchParams` + ProTable `params` 接 URL 参数(如 `/purchase-orders?status=pending`),页内手动改筛选仍可覆盖
- 交互:可回退/草稿态操作去确认弹层;不可逆/动库存操作保留确认
- 写请求幂等:axios 拦截器(`web/src/api/http.ts`)对 POST/PUT/DELETE 自动附加 `Idempotency-Key` 头(crypto.randomUUID);需要重试重放语义时才手动传固定 key

## 数据状态

- 测试一律走 `inventory_test` 库且自包含(自建自清);E2E 不清数据
- 开发库 inventory:2026-09-15 重建,种子 + 一套 14 天全链路仿真数据(采购/销售/退货/盘点/结算各单据)

## 前端验证标准

- `npm run build` 0 错是**必要不充分**:已多次 build 全绿但运行时白屏(Suspense 进 Route 子树、Vite 依赖预构建竞态)
- 前端改动必须浏览器(5173)过目页面实际渲染才算完工;改依赖/路由后先重启 dev server 再验

## 硬约束(违反即返工)

1. 禁止改 `StockCoreService` 扣减逻辑和 `mapper/StockMapper.xml` 的防穿仓 SQL(条件 UPDATE)
2. 测试只增不减;新测试必须自包含(自建数据自清理),不要用 `@Transactional` 回滚
3. PG 表/列统一小写蛇形(V6 迁移):手写 SQL 不写引号;DO 靠 `map-underscore-to-camel-case: true` 自动映射,审计字段用 `@TableField(fill = ...)`
4. Javadoc 摘要以中文句号「。」收尾(checkstyle 按字面量 `。` 匹配);禁 `select *`
5. MyBatis-Plus 空集合防护:`selectByIds`/`IN` 前必须 `isEmpty()`
6. 不引入新依赖,除非任务书明确允许
7. 动了接口/表/测试数/权限 → 同步更新 docs/API一览.md、docs/权限矩阵.md 与 README 对应章节;业务规则变更更新 docs/核心业务规则.md;同时 docs/迭代日志.md 追加一条,与代码同 commit
8. **git 不默认提交**:改完代码只报告"待提交",必须等用户明确说"提交"才 commit;一次"提交"只授权当时那一批改动,后续新改动仍需再次授权,不得当作持续授权

## 踩坑备忘

- 重启后端要按端口(8888)找 java 进程并结束,只杀 maven 壳不够;
  正常启停建议走 IDE 运行配置(InventoryApplication / web-前端(Vite)),别命令行另起实例撞端口
- Windows(MSYS/git-bash)下直接 `mvn` 有 classworlds 路径 bug,一律走 `scripts/mvn.sh` 包装脚本
- 统计测试数用 `grep -cE "@Test\b"`,别 grep `@Test`(@TestInstance 会多算)
- 本机有 HTTP 代理时 curl 带 Authorization 可能拿到假 401;验证接口走 python urllib 或浏览器 fetch
- 改实体/接口后同步 `web/src/types/phase1.ts` 与 `web/src/api/index.ts`
