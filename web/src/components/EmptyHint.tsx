// 情境化空态:表格空数据时给出"当前筛选/条件下无数据"的提示,可选带行动按钮
import { Empty, Button } from "antd";

export function EmptyHint({
  text,
  actionText,
  onAction,
}: {
  text?: string;
  actionText?: string;
  onAction?: () => void;
}) {
  return (
    <Empty
      image={Empty.PRESENTED_IMAGE_SIMPLE}
      description={text ?? "当前条件下暂无数据"}
    >
      {actionText && onAction ? (
        <Button type="primary" onClick={onAction}>
          {actionText}
        </Button>
      ) : null}
    </Empty>
  );
}
