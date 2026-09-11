# AGENT 工作手册(给 AI 编码代理的项目指南)

> 本文件是给 **Claude Code / pi / opencode 等 AI 代理** 的项目上下文。
> 派任务时把本文件全文(或路径)放进任务书开头,代理即可自洽工作。
> 人看的说明在 `README.md`,迭代历史在 `docs/迭代日志.md`。

## 0. 一句话

企业级进销存(采购/销售/调拨/盘点/调整/预警/审批/预占),前后端分离,
Java 21 + Spring Boot 3.5 + MyBatis-Plus 3.5 + PostgreSQL 16 / Vite 5 + React 18 + antd 5,
单机自用,阿里开发规范。

## 1. 目录速查

```
E:\hermes-workspace\inventory-system\
├── docker-compose.yml        PG 16(容器名 inventory-postgres,端口 5433,库 inventory,用户 inv)
├── scripts/mvn.sh            Maven 包装脚本(必用,见 §5 坑1)
├── server-java/              后端(阿里规范,~240 文件)
│   ├── checkstyle.xml        规则集(违规 0 是硬指标)
│   ├── src/main/java/com/company/inventory/
│   │   ├── InventoryApplication.java   入口:JVM 时区锁 Asia/Shanghai + 启动横幅
│   │   ├── common/constant/ErrorCode.java   所有错误码 + BIZ_CODE_* 流水类型(前端 Tag 映射要与此对齐)
│   │   ├── common/support/ApprovalGuard.java    审批资格(admin 可自批/operator 禁自批)
│   │   ├── common/support/DictReferenceRegistry.java   字典→业务表引用注册表(停用校验)
│   │   ├── controller/  17 个(每个功能一个)
│   │   ├── dto/ entity/ vo/ query/   层内按功能分包(如 dto/purchase/)
│   │   ├── mapper/      26 个 + resources/mapper/*.xml
│   │   └── service/ (+impl/)
│   ├── src/main/resources/db/  schema.sql → V2__phase1.sql → V3__dict.sql → V4__dict_type.sql(按序推)
│   └── src/test/java/   11 类 77 条(只增不减,新增测试必须自包含不依赖执行顺序)
├── web/                      前端
│   └── src/
│       ├── api/index.ts      全部接口封装(xxxApi)
│       ├── components/StatusTag.tsx   状态/角色/业务 Tag + BIZ_OPTIONS(8 种 bizCode 中文映射)
│       ├── pages/            19 个页面模块 24 文件
│       └── main.tsx          dayjs.locale("zh-cn")(日历中文,勿删)
├── docs/迭代日志.md           每次实质变更追加一条(新在上)
└── .tmp/                     任务书/报告/日志(不进 git,派任务放这里)
```

## 2. 运行环境(固定事实,别探测)

| 项 | 值 |
|---|---|
| JDK | D:\DevTools\jdk-21.0.10 |
| Maven | D:\apache-maven-3.6.3,只能经 `bash scripts/mvn.sh` |
| 后端 | 8081,通常 IDEA 在跑(InventoryApplication 运行配置) |
| 前端 | 5173,Vite dev(IDEA web-前端(Vite) 运行配置),/api 代理到 8081 |
| PG | docker 容器 inventory-postgres,5433 |
| 测试账号 | admin/admin123、zhangsan/zhang123(operator)、lisi/lisi123(viewer) |
| shell | git-bash(MSYS);node 路径给原生 `C:/...` 正斜杠形式 |

**5173/8081 是别人(IDEA)在跑:禁止 kill/重启这两个服务,改代码靠 HMR/热更,
需要后端重启时报告给调度方,不要自己重启。**

## 3. 硬约束(违反 = 返工)

1. **禁止改** `StockCoreService` 的扣减逻辑和 `mapper/StockMapper.xml` 的防穿仓 SQL
   (`deductStock` 条件 UPDATE)。任何"优化"都不允许。
2. **77 条测试只增不减**;新增测试必须自包含(自己造数据、自己清数据),
   不用 `@Transactional` 回滚(会破坏其他测试的数据隔离,已踩过坑)。
3. **PG 标识符**:表/列都是建库时带引号的 PascalCase/camelCase,
   手写 SQL 必须双引号(`"Item"`、`"itemCode"`);DO 用 `@TableName("\"Item\"")`、
   `@TableField("\"col\"")`。照抄 `StockMapper.xml` 写法。
4. **checkstyle 违规必须 0**。中文 Javadoc 摘要以中文句号「。」收尾
   (规则配的是 period=字面量 `。`);常量类字段也要中文注释;禁止 `select *`。
5. **列表接口规范**:Query DTO 绑定(@Valid)+ 全量分页,
   返回 `PageResult {rows,total,page,pageSize}`;错误体 `{statusCode,error,message}`。
   已有 17 个 Controller 全量统一,新接口照抄。
6. **时间**:数据库存东八区墙钟,接口格式 `yyyy-MM-dd HH:mm:ss`(日期 `yyyy-MM-dd`)。
   后端 Jackson 已配好,新实体日期字段沿用 `@JsonFormat`。
7. **权限**:写接口加 `@RequireRole("admin")`(或 `"admin","operator"`)。
   涉及审批的动作必须走 `ApprovalGuard`,不要在 Service 里手写角色判断。
8. **字典**:配置类枚举才进字典表;状态机/流水枚举**不进字典**,
   前端静态映射(见 `StatusTag.tsx` 的 BIZ_LABEL,新增 bizCode 时同步加)。
   字典 typeCode 建后不可改;停用校验走 `DictReferenceRegistry`,
   新业务表引用字典列时要往注册表里加一行。
9. **MyBatis-Plus 空集合**:`selectByIds`/`IN` 前必须 `isEmpty()` 防护
   (空集合会生成非法 SQL `IN ( )`,已踩过坑)。
10. **不引入新依赖**(前后端都不),除非任务书明确允许。
11. **所有注释、UI 文案、错误 message 用中文**。

## 4. 构建与验证(完工前必须全跑,数字写实测值)

```bash
cd server-java
bash ../scripts/mvn.sh test               # 期望 77+ 条全绿,无 skip
bash ../scripts/mvn.sh checkstyle:check   # 期望 0 违规
bash ../scripts/mvn.sh package            # 期望 0 错
cd ../web
npm run build                             # 期望 0 错
```

统计测试数用 `grep -cE "@Test\b"` 逐文件加,别直接 grep `@Test`
(`@TestInstance` 会多算 11 个)。

浏览器自测(如任务书要求):
- 登录 http://localhost:5173/login,admin/admin123
- React 受控输入框 JS 直接 `.value=` 无效,必须
  `Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(el,val)` + dispatch `input` 事件
- 按钮文字可能带空格("保 存"),用 `includes` 或去空格匹配
- curl 直连 8081 带 Authorization 会被本机代理吞 → 假 401;
  验证接口走 python urllib 或浏览器内 `fetch()`(带 vite 代理)

## 5. 踩坑备忘(历史事故,勿复现)

1. `mvn` 在 bash 下 classworlds 路径 bug → 一律 `bash scripts/mvn.sh`
2. 杀后端进程:杀 mvn 壳不够,`netstat -ano | grep 8081` 找 java PID 强杀
3. opencode 长任务可能中途断(免费模型限额),报告写到 .tmp/ 便于断点续会话
4. 派任务书放 `.tmp/TASK-*.md`,agent 完工报告放 `.tmp/*-report.md`
5. 改实体/接口后,前端 `web/src/types/phase1.ts` 和 `api/index.ts` 要同步
6. antd 日历英文 = 缺 `dayjs.locale("zh-cn")`;ConfigProvider locale 只管按钮文案
7. 前端页面引用字典:配置类下拉用 `dictApi.getType("xxx")`,别写死选项
8. 新建字典类型后,业务表单要用它还得单独接线(字典不替代 UI)
9. Item 表没有 updatedAt 列;Dict/DictType 有
10. 测试库 = inventory_test(同容器),生产库 = inventory;测试往 test 库写

## 6. 派工约定(调度方规则,代理遵守)

- 任务书 = `.tmp/TASK-xxx.md`,开工前先完整读;完工报告 = `.tmp/xxx-report.md`
- 报告必须含:改动文件清单、测试数(实测)、验证结果、遗留问题
- 禁止"应该/可能"式自报:每条验证都要有可复核的证据(命令输出/截图/库查询)
- 文档同步:动了接口/表/测试数/权限 → 同批更新 `README.md` 对应章节 +
  追加一条 `docs/迭代日志.md`(格式见文件头),与代码同一个 commit
