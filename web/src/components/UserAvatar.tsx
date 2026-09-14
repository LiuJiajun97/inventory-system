// 用户头像首字圆块

export function UserAvatar({
  name,
  style,
}: {
  name?: string;
  /** 附加样式(如作为下拉菜单入口时的 pointer 光标) */
  style?: React.CSSProperties;
}) {
  const first = (name ?? "").trim().charAt(0).toUpperCase() || "U";
  return (
    <span className="user-avatar" style={style}>
      {first}
    </span>
  );
}