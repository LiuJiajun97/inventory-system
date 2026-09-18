// 列表页表格偏好(列宽拖拽 / 列显隐勾选 / 列拖拽排序)公共封装
// 配置按用户存服务端(sys_user_table_pref,Redis 缓存),config 结构 {widths, hidden, order}
//
// 用法(每个 ProTable 列表页):
//   const { columns, scroll, columnsState, onColumnsChange, components } =
//     useResizableColumns(columns, "purchase-list");
//   <ProTable columns={...rcColumns} scroll={scroll} columnsState={columnsState}
//     onColumnsStateChange={onColumnsChange} components={components}
//     options={{ density: false, reload: false, fullScreen: false,
//               setting: { checkable: true, draggable: true, checkedReset: false } }} />

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Popconfirm } from "antd";
import type { CSSProperties, ReactNode, SyntheticEvent } from "react";
import { Resizable } from "react-resizable";
import type { ResizeCallbackData } from "react-resizable";
import type { ColumnsState, ProColumns } from "@ant-design/pro-components";
import { tablePrefApi } from "../api";
import { getUser } from "../auth/useAuth";

/** 单页表格偏好:widths 列宽(key→像素)、hidden 隐藏列 key、order 列序(可见顺序)。 */
export type TablePrefConfig = {
  widths?: Record<string, number>;
  hidden?: string[];
  order?: string[];
};

/** 列 key 兜底前缀(列既无 key 也无字符串 dataIndex 时用标题/下标生成)。 */
const KEY_PREFIX = "__col__";

/**
 * 取列的稳定 key(columnsState 与 config 共用):
 * 优先列 key,其次字符串 dataIndex,再次标题,最后按下标兜底。
 */
function colKeyOf<T>(col: ProColumns<T>, index: number): string {
  if (typeof col.key === "string" && col.key) return col.key;
  if (typeof col.dataIndex === "string" && col.dataIndex) return col.dataIndex;
  if (typeof col.title === "string" && col.title) return `${KEY_PREFIX}title:${col.title}`;
  return `${KEY_PREFIX}idx:${index}`;
}

/**
 * 判断保护列(不开放拖宽、禁隐藏禁调序):标题为"操作"/"行号",或 dataIndex 为行号类。
 */
function isProtectedColumn<T>(col: ProColumns<T>): boolean {
  if (typeof col.title === "string" && (col.title === "操作" || col.title === "行号")) {
    return true;
  }
  return typeof col.dataIndex === "string" && (col.dataIndex === "lineNo" || col.dataIndex === "index");
}

/** 列宽最小下限:max(56, 默认宽 × 55%),不能拖没。 */
function minWidthOf(defaultWidth: number | undefined): number {
  return Math.max(56, Math.round((defaultWidth ?? 0) * 0.55));
}

/** 未写 width 的展示列兜底宽:fixed 布局下无 width 列会被压到 0,兜底避免压扁。 */
const FALLBACK_WIDTH = 120;

// ---------------------------------------------------------------------------
// 模块级缓存:同一用户 26 页共享一次 GET /table-prefs(按 userId 分桶 + 请求去重)
// ---------------------------------------------------------------------------
const prefsByUser = new Map<number, Record<string, TablePrefConfig>>();
const inflightByUser = new Map<number, Promise<Record<string, TablePrefConfig>>>();

/**
 * 拉取当前登录用户的全部页面表格偏好(模块级缓存,失败返回空对象不阻断渲染)。
 */
export async function fetchTablePrefs(): Promise<Record<string, TablePrefConfig>> {
  const userId = getUser()?.id;
  if (userId == null) return {};
  const cached = prefsByUser.get(userId);
  if (cached) return cached;
  let pending = inflightByUser.get(userId);
  if (!pending) {
    pending = tablePrefApi
      .get()
      .then((data) => {
        const map = (data ?? {}) as Record<string, TablePrefConfig>;
        prefsByUser.set(userId, map);
        return map;
      })
      .catch(() => ({} as Record<string, TablePrefConfig>))
      .finally(() => {
        inflightByUser.delete(userId);
      });
    inflightByUser.set(userId, pending);
  }
  return pending;
}

// ---------------------------------------------------------------------------
// 可拖拽列表头单元格(react-resizable 官方 antd 接入模式)
// ---------------------------------------------------------------------------

/** 可拖拽表头单元格 props:antd th 全量属性 + 列宽 + 拖拽回调。 */
type ResizableTitleProps = {
  width?: number;
  onResize?: (e: SyntheticEvent, data: ResizeCallbackData) => void;
  style?: CSSProperties;
  children?: ReactNode;
  [attr: string]: unknown;
};

/** 可拖拽列表头:无列宽或无 onResize(保护列)时退化为普通 th。 */
function ResizableTitle({ onResize, width, ...restProps }: ResizableTitleProps) {
  if (!width || !onResize) {
    return <th {...(restProps as React.ThHTMLAttributes<HTMLTableCellElement>)} />;
  }
  return (
    <Resizable
      width={width}
      height={0}
      onResize={onResize}
      draggableOpts={{ enableUserSelectHack: false }}
    >
      <th {...(restProps as React.ThHTMLAttributes<HTMLTableCellElement>)} />
    </Resizable>
  );
}

/** useResizableColumns 的返回结构(ProTable 直接透传各字段)。 */
export interface ResizableColumnsResult<T> {
  /** 注入列宽/ellipsis/表头拖拽后的列(顺序与显隐交给 columnsState 机制,此数组保持代码顺序)。 */
  columns: ProColumns<T>[];
  /** 动态 scroll.x = 可见列宽之和(无可拖列宽时不传)。 */
  scroll: { x: number } | undefined;
  /** 受控列状态:show/order 由服务端 config 构造,disable 标记保护列(操作/行号)。 */
  columnsState: { value: Record<string, ColumnsState>; onChange: (map: Record<string, ColumnsState>) => void };
  /** 显隐/列序变更回调(与 columnsState.onChange 同一函数,便于 ProTable onColumnsStateChange 透传)。 */
  onColumnsChange: (map: Record<string, ColumnsState>) => void;
  /** 表头单元格替换(components.header.cell = ResizableTitle)。 */
  components: { header: { cell: (props: ResizableTitleProps) => ReactNode } };
  /** 列设置选项(接 ProTable options.setting):setting 弹窗内带"恢复默认"按钮。 */
  optionSetting: {
    checkable: boolean;
    draggable: boolean;
    checkedReset: boolean;
    extra: ReactNode;
  };
  /** 一键恢复默认:显隐/列序/列宽全部还原,并立即清除服务端该页配置。 */
  resetDefaults: () => void;
}

/**
 * 列表页 ProTable 列偏好 hook:列宽拖拽 + 列显隐勾选 + 列拖拽排序,配置持久化到服务端。
 *
 * <p>读:首次拉 fetchTablePrefs()(模块级缓存),取本页 config;
 * 某列宽 = config.widths[key] ?? 列代码默认宽;显隐/列序经受控 columnsState 生效(保护列 disable)。
 * 写:拖宽/显隐/列序变更后合并 config 并防抖 ~1s 调 save;拖宽实时生效(本地 state)。</p>
 *
 * @param columns 页面代码列定义(仅表格展示列,不含 hideInTable 筛选列)
 * @param pageKey 唯一页面标识(路由段,如 purchase-list)
 */
export function useResizableColumns<T>(
  columns: ProColumns<T>[],
  pageKey: string,
): ResizableColumnsResult<T> {
  /** 服务端 config(加载完成后持有,作为合并基线)。 */
  const configRef = useRef<TablePrefConfig>({});
  /** 防抖定时器(拖宽/显隐/列序共用一个 ~1s 合并保存)。 */
  const timerRef = useRef<number | null>(null);
  /** 服务端 config 触发重渲染(初始加载后填一次)。 */
  const [pref, setPref] = useState<TablePrefConfig>({});
  /** 本地拖宽覆盖值(拖拽过程实时生效,防抖后才落服务端)。 */
  const [widths, setWidths] = useState<Record<string, number>>({});
  /** 列 key 列表(代码顺序,供 onChange 时过滤出本 hook 已知列)。 */
  const keysRef = useRef<string[]>([]);

  /** 列 key(代码顺序)。 */
  const keys = useMemo(() => columns.map((col, i) => colKeyOf(col, i)), [columns]);
  keysRef.current = keys;

  /** 列默认宽 key→width(算最小下限用)。 */
  const defaultWidths = useMemo(() => {
    const m: Record<string, number> = {};
    columns.forEach((col, i) => {
      const w = col.width;
      m[colKeyOf(col, i)] = typeof w === "number" ? w : 0;
    });
    return m;
  }, [columns]);

  // 首次拉本页 config(模块级缓存,多页只打一次接口)
  useEffect(() => {
    let alive = true;
    fetchTablePrefs().then((all) => {
      if (!alive) return;
      const config = all[pageKey] ?? {};
      configRef.current = config;
      setPref(config);
    });
    return () => {
      alive = false;
    };
  }, [pageKey]);

  // 卸载时清未触发的防抖保存
  useEffect(
    () => () => {
      if (timerRef.current != null) window.clearTimeout(timerRef.current);
    },
    [],
  );

  /** 合并 config 并防抖 ~1s 保存(拖宽/显隐/列序共用)。 */
  const mergeSave = useCallback(
    (patch: Partial<TablePrefConfig>) => {
      configRef.current = { ...configRef.current, ...patch };
      if (timerRef.current != null) window.clearTimeout(timerRef.current);
      timerRef.current = window.setTimeout(() => {
        timerRef.current = null;
        tablePrefApi.save(pageKey, configRef.current).catch(() => undefined);
      }, 1000);
    },
    [pageKey],
  );

  /**
   * 一键恢复默认:本地显隐/列序/列宽全部还原,并立即把服务端该页配置置空(不走防抖)。
   */
  const resetDefaults = useCallback(() => {
    // 清未触发的防抖保存,避免恢复后被旧配置覆盖回去
    if (timerRef.current != null) {
      window.clearTimeout(timerRef.current);
      timerRef.current = null;
    }
    configRef.current = {};
    setPref({});
    setWidths({});
    tablePrefApi.save(pageKey, {}).catch(() => undefined);
  }, [pageKey]);

  /** 拖宽回调:夹到最小下限后实时生效 + 防抖保存。 */
  const handleResize = useCallback(
    (key: string, width: number) => {
      const clamped = Math.max(minWidthOf(defaultWidths[key]), width);
      setWidths((prev) => ({ ...prev, [key]: clamped }));
      mergeSave({
        widths: { ...(configRef.current.widths ?? {}), [key]: clamped },
      });
    },
    [defaultWidths, mergeSave],
  );

  // 注入列宽 / ellipsis / 表头拖拽(列序与显隐由 columnsState 机制处理,这里保持代码顺序)
  const rcColumns = useMemo<ProColumns<T>[]>(
    () =>
      columns.map((col, i) => {
        const key = colKeyOf(col, i);
        // 兜底宽:列没写 width 时用 FALLBACK_WIDTH(fixed 布局下无 width 列会被压成 0 宽)
        const width = widths[key] ?? (pref.widths?.[key] ?? (typeof col.width === "number" ? col.width : FALLBACK_WIDTH));
        const protectedCol = isProtectedColumn(col);
        const onHeaderCell =
          !protectedCol && typeof width === "number" && width > 0
            ? () => ({
                key,
                width,
                onResize: (_e: SyntheticEvent, data: ResizeCallbackData) =>
                  handleResize(key, data.size.width),
              })
            : undefined;
        return {
          ...col,
          key,
          ...(typeof width === "number" ? { width } : {}),
          ellipsis: true,
          onHeaderCell,
        };
      }),
    [columns, widths, pref, handleResize],
  );

  // 受控列状态:show/order 由 config 构造,保护列 disable(不许隐藏/调序)
  const onColumnsChange = useCallback(
    (map: Record<string, ColumnsState>) => {
      const known = keysRef.current;
      // 列序:ProTable 的 order 升序排列(越小越靠左),按升序转成存储的"从左到右"序列
      const order = known
        .slice()
        .sort((a, b) => (map[a]?.order ?? 0) - (map[b]?.order ?? 0));
      // 隐藏:show === false 的列(show 缺省视为可见)
      const hidden = known.filter((k) => map[k]?.show === false);
      mergeSave({ hidden, order });
      // 同步受控状态:受控模式下必须本地更新 pref,否则 ProTable 列显隐/列序不生效(会回弹)
      setPref((prev) => ({ ...prev, hidden, order }));
    },
    [mergeSave],
  );

  const columnsState = useMemo(() => {
    const hiddenSet = new Set(pref.hidden ?? []);
    const orderIndex = new Map<string, number>();
    (pref.order ?? []).forEach((k, i) => orderIndex.set(k, i));
    const value: Record<string, ColumnsState> = {};
    columns.forEach((col, i) => {
      const key = colKeyOf(col, i);
      value[key] = {
        show: !hiddenSet.has(key),
        // order 升序 = 从左到右:config.order 中越靠前的列 order 越小
        order: orderIndex.has(key) ? (orderIndex.get(key) as number) : i,
        ...(isProtectedColumn(col) ? { disable: true } : {}),
      };
    });
    return { value, onChange: onColumnsChange };
  }, [columns, pref, onColumnsChange]);

  // 动态 scroll.x = 可见列宽之和(拖拽后 widths 优先);无有效列宽时不传
  const rcScroll = useMemo(() => {
    const hiddenSet = new Set(pref.hidden ?? []);
    let sum = 0;
    columns.forEach((col, i) => {
      const key = colKeyOf(col, i);
      if (hiddenSet.has(key)) return;
      const w = widths[key] ?? (pref.widths?.[key] ?? (typeof col.width === "number" ? col.width : FALLBACK_WIDTH));
      sum += w;
    });
    return { x: sum };
  }, [columns, widths, pref]);

  const components = useMemo(
    () => ({ header: { cell: ResizableTitle } }),
    [],
  );

  // 列设置选项:setting 弹窗标题区带"恢复默认"按钮(extra 插槽,checkedReset:false 下无内置重置,由本按钮承担)
  const optionSetting = useMemo(
    () => ({
      checkable: true,
      draggable: true,
      checkedReset: false,
      extra: (
        <Popconfirm
          title="恢复默认"
          description="该页列宽、显隐、列序将还原为默认,确定吗?"
          okText="恢复"
          cancelText="取消"
          onConfirm={resetDefaults}
        >
          <a>恢复默认</a>
        </Popconfirm>
      ),
    }),
    [resetDefaults],
  );

  return {
    columns: rcColumns,
    scroll: rcScroll,
    columnsState,
    onColumnsChange,
    components,
    optionSetting,
    resetDefaults,
  };
}
