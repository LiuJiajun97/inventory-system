// ProTable request 分页适配封装(全仓列表页统一模板)
// 背景:各列表页此前各自手写两件样板——①入参映射 current→page / pageSize→20;
// ②回包适配后端 {rows,total,...} -> ProTable {data,success,total}。
// 收敛到本封装:页面只提供"api 参数构造 + api 调用",映射与适配统一在此完成。

/**
 * 分页接口 ProTable request 封装。
 * @param buildQuery 由 ProTable 入参构造后端查询参数(不含 page/pageSize,封装自动附加);
 *                   页内特有副作用(导出筛选快照 setFilterParams、仓库范围合并等)写在内部
 * @param fetchRows 后端分页列表接口本身(如 inboundApi.list),参数含 page/pageSize
 * @param afterFetched 可选:拿到回包后的页内副作用(如 ?docNo= 跳转单条命中自动开详情)
 * @returns 传给 ProTable 的 request 函数,返回标准形态 {data,success,total}
 */
export function proTableRequest<
  TParams,
  TQuery extends { page?: number; pageSize?: number },
  TRow,
>(
  buildQuery: (params: TParams) => Omit<TQuery, "page" | "pageSize">,
  fetchRows: (query: TQuery) => Promise<{ rows: TRow[]; total: number }>,
  afterFetched?: (res: { rows: TRow[]; total: number }, params: TParams) => void,
): (params: TParams & { current?: number; pageSize?: number }) => Promise<{ data: TRow[]; success: true; total: number }> {
  return async (params: TParams & { current?: number; pageSize?: number }) => {
    // 入参映射:ProTable current/pageSize -> 后端 page/pageSize(缺省第 1 页、每页 20)
    const query: TQuery = {
      ...buildQuery(params),
      page: params.current ?? 1,
      pageSize: params.pageSize ?? 20,
    } as TQuery;
    const res = await fetchRows(query);
    afterFetched?.(res, params);
    // 回包适配:后端 {rows,total} -> ProTable {data,success,total}
    return { data: res.rows, success: true, total: res.total };
  };
}
