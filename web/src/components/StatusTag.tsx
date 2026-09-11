// 统一状态 Tag(SPEC 颜色规则)
// 启用=green、停用=default、在库=blue、已出库=orange、过期=red
// 角色:admin=red、operator=blue、viewer=green

import { Tag } from "antd";
import type { Role } from "../types";

type TagTone = "enabled" | "disabled" | "inbound" | "outbound" | "expired";

const TONE_CLASS: Record<TagTone, string> = {
  enabled: "tag-status-enabled",
  disabled: "tag-status-disabled",
  inbound: "tag-status-inbound",
  outbound: "tag-status-outbound",
  expired: "tag-status-expired",
};

export function StatusTag({
  status,
  label,
}: {
  status: "enabled" | "disabled" | "inbound" | "outbound" | "expired";
  label?: string;
}) {
  return (
    <Tag bordered className={TONE_CLASS[status]}>
      {label ?? defaultLabel(status)}
    </Tag>
  );
}

function defaultLabel(s: TagTone): string {
  switch (s) {
    case "enabled":
      return "启用";
    case "disabled":
      return "停用";
    case "inbound":
      return "在库";
    case "outbound":
      return "已出库";
    case "expired":
      return "过期";
  }
}

// 角色 Tag(admin/operator/viewer)
const ROLE_LABEL: Record<Role, string> = {
  admin: "管理员",
  operator: "库员",
  viewer: "查看",
};
const ROLE_CLASS: Record<Role, string> = {
  admin: "tag-role-admin",
  operator: "tag-role-operator",
  viewer: "tag-role-viewer",
};

export function RoleTag({ role }: { role: Role }) {
  return (
    <Tag bordered className={ROLE_CLASS[role]}>
      {ROLE_LABEL[role]}
    </Tag>
  );
}

// 业务 Tag(入库/出库) —— 用于流水查询
const BIZ_LABEL: Record<string, { text: string; cls: string }> = {
  inbound: { text: "入库", cls: "tag-status-inbound" },
  outbound: { text: "出库", cls: "tag-status-outbound" },
};

export function BizTag({ biz }: { biz: string }) {
  const it = BIZ_LABEL[biz];
  if (!it) return <Tag bordered>{biz}</Tag>;
  return (
    <Tag bordered className={it.cls}>
      {it.text}
    </Tag>
  );
}