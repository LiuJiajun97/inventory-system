## 5. API 一览(前缀 `/api/v1`,全中文描述,列表统一 `{rows,total,page,pageSize}`)

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| POST | `/auth/login` `/auth/password` `/auth/me` | 登录 / 改密 / 当前用户(登录响应 user 新增 roles 数组) | 公开 / 登录 |
| POST | `/auth/logout` | 登出:吊销当前 token 的 jti(登出后旧 token 立即 401;无 jti 旧 token 自然过期),返回 `{ok:true}` | 登录 |
| GET | `/auth/menus` | 当前用户菜单树(多角色并集,仅目录+菜单,附按钮权限码列表) | 登录 |
| GET | `/auth/perm-check` | 权限码探针(校验 @RequirePerm,码 purchase-order:approve) | 登录+权限码 |
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
| GET/PUT | `/table-prefs` `/table-prefs/{pageKey}`(V21) | 表格列偏好:拉当前用户全部页面配置 / 整页覆盖存列宽+显隐+列序(config JSONB,Redis 缓存 `pref:table:{uid}` TTL300s 写失效) | 登录 |

完整契约以 Swagger 为准:`http://127.0.0.1:8888/docs`。

**横切契约(企业级横切能力)**
- **幂等 `Idempotency-Key` 头**:全部 `/api/v1` 写接口(POST/PUT/DELETE)支持标准幂等键。无 key 行为不变;同 key 第二次请求重放首次成功响应(原样 status/contentType/body,TTL 5 分钟);首次仍在进行中时并发重复 → **429 `{statusCode:429,error:"idempotent_conflict",message:"重复请求,请稍后重试"}`**;首次请求失败则释放 key,同 key 重试正常执行。前端 axios 拦截器对写请求自动附加 `crypto.randomUUID()`。
- **登录防爆破 429**:同一用户名连续 5 次登录失败锁 5 分钟(`login.max-attempts`/`login.lock-seconds` 可调),锁定中一律 429 `{statusCode:429,code:"auth_locked",error:"Too Many Requests",message:"登录失败次数过多,请 N 秒后重试"}`;到期自动重置,登录成功清零。
- **链路追踪 `X-Trace-Id` 响应头**:所有请求(含 404/异常)均回 `X-Trace-Id`(本地生成 16 位 hex;请求头带 W3C `traceparent` 时透传其 trace-id);全部统一错误体新增可选字段 `traceId`(与响应头一致),用于前后端联调排障。
- **token 有效期**:JWT 从 8 小时缩短为 **2 小时**(`jwt.expires-seconds: 7200`),签发含 `jti` claim,配合 logout 吊销。
- **可观测性端点**(`/actuator/**`,无需登录,单机自用;生产需网关鉴权):`GET /actuator/health`(UP/DOWN)、`GET /actuator/info`、`GET /actuator/metrics`。
