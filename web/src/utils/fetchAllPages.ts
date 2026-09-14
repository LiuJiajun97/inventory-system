// 分页全量拉取工具(TASK-v22b B3):后端分页接口 pageSize 上限 200,
// 图表聚合需要全量数据,按 200/页循环拉取;total 超过 maxRows(默认 1000)时截断
// (本地库数据量远小于 1000,截断仅作保险)

export interface Paged<T> {
  rows: T[];
  total: number;
}

/**
 * 分页拉取全部数据
 * @param fetchPage (page, pageSize=200) => 分页响应
 * @param maxRows 最多取多少行(超过即截断),默认 1000
 */
export async function fetchAllPages<T>(
  fetchPage: (page: number, pageSize: number) => Promise<Paged<T>>,
  maxRows = 1000,
): Promise<T[]> {
  const pageSize = 200;
  let page = 1;
  const all: T[] = [];
  for (;;) {
    const res = await fetchPage(page, pageSize);
    all.push(...res.rows);
    // 已拉完 / 达上限 / 末页,停止
    if (all.length >= res.total || all.length >= maxRows || res.rows.length < pageSize) break;
    page += 1;
  }
  return all.slice(0, maxRows);
}
