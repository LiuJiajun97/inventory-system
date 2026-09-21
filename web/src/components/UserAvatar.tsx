// 用户头像首字圆块

export function UserAvatar({
  name,
  style,
  onClick,
}: {
  name?: string;
  /** 附加样式(如作为下拉菜单入口时的 pointer 光标) */
  style?: React.CSSProperties;
  /** 点击处理(作为下拉菜单入口时 antd Dropdown 会向子元素注入,必须透传到 DOM 元素) */
  onClick?: React.MouseEventHandler<HTMLSpanElement>;
}) {
  const first = (name ?? "").trim().charAt(0).toUpperCase() || "U";
  return (
    <span className="user-avatar" style={style} onClick={onClick}>
      {first}
    </span>
  );
}
