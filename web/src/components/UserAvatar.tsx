// 用户头像首字圆块

export function UserAvatar({ name }: { name?: string }) {
  const first = (name ?? "").trim().charAt(0).toUpperCase() || "U";
  return <span className="user-avatar">{first}</span>;
}