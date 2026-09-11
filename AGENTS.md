# AGENTS.md

本文件给 AI 编码代理(Claude Code / Codex / pi / opencode 等)提供项目上下文。
人看的文档:README.md(总览)、docs/迭代日志.md(变更流水)。

## 项目

企业级进销存(采购/销售/调拨/盘点/调整/预警/审批/预占),单机自用。
后端 `server-java/`:Java 21 + Spring Boot 3.5 + MyBatis-Plus 3.5 + PostgreSQL 16。
前端 `web/`:Vite + React 18 + antd 5。阿里开发规范,注释与 UI 文案全中文。

## 常用命令

```bash
# 构建/测试(server-java 下,Maven 必须经包装脚本,bash 下直接 mvn 有 classworlds 路径 bug)
bash scripts/mvn.sh test               # 后端测试(当前 77 条,必须全绿)
bash scripts/mvn.sh checkstyle:check   # checkstyle(必须 0 违规)
bash scripts/mvn.sh package            # 构建
bash scripts/mvn.sh spring-boot:run    # 启动后端(8081)

# 前端(web 下)
npm run dev                            # dev(5173,代理 /api → 8081)
npm run build                          # 构建(必须 0 错)

# 数据库(docker 容器 inventory-postgres,端口 5433,库 inventory,用户 inv)
docker exec -i inventory-postgres psql -U inv -d inventory < server-java/src/main/resources/db/schema.sql
docker compose up -d                   # 起 PG
```

完工前必须跑:三件套(test/checkstyle/package)+ `npm run build`,数字写实测值。

## 运行环境

- 后端 8081、前端 5173 通常由 IDEA 运行配置在跑,**禁止 kill/重启这两个服务**,靠 HMR/热更;需重启则报告给调度方
- PG:容器 `inventory-postgres`,端口 5433,库 `inventory`(生产)/`inventory_test`(测试)
- 账号:admin/admin123、zhangsan/zhang123(operator)、lisi/lisi123(viewer)
- shell 为 git-bash(MSYS);给原生工具的 Windows 路径用 `C:/...` 正斜杠

## 架构约定

- 后端分层:`controller → service(/impl) → mapper → entity(DO)`,层内按功能分包(dto/purchase/ 等)
- 列表接口统一:Query DTO 绑定(@Valid)+ 全量分页,返回 `{rows,total,page,pageSize}`;错误体 `{statusCode,error,message}`
- 时间:东八区(JVM 已锁 Asia/Shanghai),接口格式 `yyyy-MM-dd HH:mm:ss`,日期 `yyyy-MM-dd`
- 权限:写接口加 `@RequireRole("admin")`;审批动作必须走 `common/support/ApprovalGuard`(admin 可自批、operator 禁自批),不要手写角色判断
- 字典:配置类枚举才进字典表;状态机/流水枚举不进,前端静态映射(`web/src/components/StatusTag.tsx` 的 BIZ_LABEL,新增 bizCode 同步加,与 `ErrorCode.BIZ_CODE_*` 对齐);业务表引用字典列的停用校验在 `DictReferenceRegistry`,新表要加注册

## 硬约束(违反即返工)

1. 禁止改 `StockCoreService` 扣减逻辑和 `mapper/StockMapper.xml` 的防穿仓 SQL(条件 UPDATE)
2. 测试只增不减;新测试必须自包含(自建数据自清理),不要用 `@Transactional` 回滚
3. PG 表/列是带引号的 PascalCase/camelCase:手写 SQL 必须双引号,DO 用 `@TableName("\"Item\"")`/`@TableField("\"col\"")`
4. Javadoc 摘要以中文句号「。」收尾(checkstyle 按字面量 `。` 匹配);禁 `select *`
5. MyBatis-Plus 空集合防护:`selectByIds`/`IN` 前必须 `isEmpty()`
6. 不引入新依赖,除非任务书明确允许
7. 动了接口/表/测试数/权限 → 同步更新 README.md 对应章节 + docs/迭代日志.md 追加一条,与代码同 commit

## 踩坑备忘

- 杀后端:`netstat -ano | grep 8081` 找 java PID 强杀,杀 mvn 壳不够
- 统计测试数用 `grep -cE "@Test\b"`,别 grep `@Test`(@TestInstance 会多算)
- curl 直连 8081 带 Authorization 会被本机代理吞成假 401;验证接口走 python urllib 或浏览器 fetch
- 改实体/接口后同步 `web/src/types/phase1.ts` 与 `web/src/api/index.ts`
- 派任务:任务书放 `.tmp/TASK-*.md`,完工报告放 `.tmp/*-report.md`(改动清单/实测测试数/验证证据/遗留)
