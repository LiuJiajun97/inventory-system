// 字典管理页(admin 才可见操作列)
// 三张卡片按类型分组，行内编辑，Switch 状态切换

import { useEffect, useState, useCallback } from "react";
import {
  Card,
  Row,
  Col,
  List,
  Switch,
  Input,
  InputNumber,
  Modal,
  Form,
  Button,
  Tag,
  Spin,
  Space,
  App,
} from "antd";
import { PlusOutlined, ReloadOutlined } from "@ant-design/icons";
import { dictApi } from "../../api";
import type { DictItem } from "../../types/phase1";

const DICT_TYPES = [
  { type: "warehouseType", label: "仓库类型" },
  { type: "itemCategory", label: "物品分类" },
  { type: "settleMethod", label: "结算方式" },
] as const;

type DictType = (typeof DICT_TYPES)[number]["type"];

interface DictState {
  data: DictItem[];
  loading: boolean;
  error: boolean;
}

export function DictPage() {
  const { message } = App.useApp();
  const [states, setStates] = useState<Record<DictType, DictState>>({
    warehouseType: { data: [], loading: false, error: false },
    itemCategory: { data: [], loading: false, error: false },
    settleMethod: { data: [], loading: false, error: false },
  });
  const [modalOpen, setModalOpen] = useState(false);
  const [modalType, setModalType] = useState<DictType>("warehouseType");
  const [form] = Form.useForm();

  const loadType = useCallback(async (type: DictType) => {
    setStates((prev) => ({
      ...prev,
      [type]: { ...prev[type], loading: true, error: false },
    }));
    try {
      const res = await dictApi.getAll(type);
      setStates((prev) => ({
        ...prev,
        [type]: { data: res, loading: false, error: false },
      }));
    } catch {
      setStates((prev) => ({
        ...prev,
        [type]: { ...prev[type], loading: false, error: true },
      }));
    }
  }, []);

  const loadAll = useCallback(() => {
    DICT_TYPES.forEach(({ type }) => loadType(type));
  }, [loadType]);

  useEffect(() => {
    loadAll();
  }, [loadAll]);

  const openCreateModal = (type: DictType) => {
    setModalType(type);
    form.resetFields();
    form.setFieldsValue({ dictType: type, sortOrder: 0 });
    setModalOpen(true);
  };

  const onCreate = async () => {
    const v = await form.validateFields();
    try {
      await dictApi.create({
        dictType: v.dictType,
        dictKey: v.dictKey,
        dictLabel: v.dictLabel,
        sortOrder: v.sortOrder,
        status: 1,
      });
      message.success("字典项创建成功");
      setModalOpen(false);
      form.resetFields();
      loadType(modalType);
    } catch {
      // 拦截器已处理
    }
  };

  const onToggleStatus = async (type: DictType, record: DictItem) => {
    const newStatus = record.status === 1 ? 0 : 1;
    try {
      await dictApi.setStatus(record.id, newStatus);
      message.success(newStatus === 1 ? "已启用" : "已停用");
      loadType(type);
    } catch {
      // 拦截器已处理
    }
  };

  return (
    <div style={{ padding: 24 }}>
      <div style={{ marginBottom: 16, display: "flex", justifyContent: "space-between", alignItems: "center" }}>
        <h2 style={{ margin: 0 }}>字典管理</h2>
        <Button icon={<ReloadOutlined />} onClick={loadAll}>
          刷新
        </Button>
      </div>

      <Row gutter={[24, 24]}>
        {DICT_TYPES.map(({ type, label }) => {
          const state = states[type];
          return (
            <Col xs={24} lg={12} key={type}>
              <Card
                title={
                  <Space>
                    <span>{label}</span>
                    <Tag>{state.data.length} 项</Tag>
                  </Space>
                }
                extra={
                  <Button
                    type="text"
                    icon={<PlusOutlined />}
                    onClick={() => openCreateModal(type)}
                  >
                    新增项
                  </Button>
                }
                style={{ height: "100%" }}
              >
                {state.loading ? (
                  <div style={{ textAlign: "center", padding: 24 }}>
                    <Spin />
                  </div>
                ) : state.error ? (
                  <div style={{ textAlign: "center", padding: 24, color: "#ff4d4f" }}>
                    加载失败
                  </div>
                ) : state.data.length === 0 ? (
                  <div style={{ textAlign: "center", padding: 24, color: "#999" }}>
                    暂无字典项
                  </div>
                ) : (
                  <List
                    dataSource={state.data}
                    renderItem={(item) => (
                      <DictItemRow
                        key={item.id}
                        item={item}
                        dictType={type}
                        onReload={() => loadType(type)}
                      />
                    )}
                  />
                )}
              </Card>
            </Col>
          );
        })}
      </Row>

      <Modal
        title="新增字典项"
        open={modalOpen}
        onOk={onCreate}
        onCancel={() => {
          setModalOpen(false);
          form.resetFields();
        }}
        width={420}
      >
        <Form form={form} layout="vertical" requiredMark={false}>
          <Form.Item label="字典类型" name="dictType">
            <Input disabled />
          </Form.Item>
          <Form.Item
            label="编码"
            name="dictKey"
            rules={[{ required: true, message: "编码必填" }]}
          >
            <Input placeholder="如 raw / cold" />
          </Form.Item>
          <Form.Item
            label="中文标签"
            name="dictLabel"
            rules={[{ required: true, message: "标签必填" }]}
          >
            <Input placeholder="如 原材料仓" />
          </Form.Item>
          <Form.Item label="排序" name="sortOrder">
            <InputNumber min={0} style={{ width: "100%" }} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}

function DictItemRow({
  item,
  dictType,
  onReload,
}: {
  item: DictItem;
  dictType: DictType;
  onReload: () => void;
}) {
  const { message } = App.useApp();
  const [editingLabel, setEditingLabel] = useState(false);
  const [editingSort, setEditingSort] = useState(false);
  const [labelValue, setLabelValue] = useState(item.dictLabel);
  const [sortValue, setSortValue] = useState(item.sortOrder);

  const saveLabel = async () => {
    if (labelValue === item.dictLabel) {
      setEditingLabel(false);
      return;
    }
    try {
      await dictApi.update(item.id, { dictLabel: labelValue });
      message.success("标签更新成功");
      setEditingLabel(false);
      onReload();
    } catch {
      message.error("标签更新失败");
      setLabelValue(item.dictLabel);
      setEditingLabel(false);
    }
  };

  const saveSort = async () => {
    if (sortValue === item.sortOrder) {
      setEditingSort(false);
      return;
    }
    try {
      await dictApi.update(item.id, { sortOrder: sortValue });
      message.success("排序更新成功");
      setEditingSort(false);
      onReload();
    } catch {
      message.error("排序更新失败");
      setSortValue(item.sortOrder);
      setEditingSort(false);
    }
  };

  const isDisabled = item.status === 0;

  return (
    <List.Item
      style={{
        opacity: isDisabled ? 0.5 : 1,
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        padding: "8px 0",
      }}
    >
      <Space style={{ flex: 1 }}>
        {editingLabel ? (
          <Input
            size="small"
            value={labelValue}
            onChange={(e) => setLabelValue(e.target.value)}
            onPressEnter={saveLabel}
            onBlur={saveLabel}
            onKeyDown={(e) => {
              if (e.key === "Escape") {
                setLabelValue(item.dictLabel);
                setEditingLabel(false);
              }
            }}
            autoFocus
            style={{ width: 120 }}
          />
        ) : (
          <span
            style={{
              cursor: "pointer",
              color: isDisabled ? "#999" : undefined,
              textDecoration: isDisabled ? "line-through" : undefined,
            }}
            onClick={() => setEditingLabel(true)}
          >
            {item.dictLabel}
          </span>
        )}
        <span
          style={{
            fontFamily: "monospace",
            fontSize: 12,
            color: "#999",
          }}
        >
          {item.dictKey}
        </span>
        {editingSort ? (
          <InputNumber
            size="small"
            value={sortValue}
            onChange={(v) => setSortValue(v ?? 0)}
            onPressEnter={saveSort}
            onBlur={saveSort}
            onKeyDown={(e) => {
              if (e.key === "Escape") {
                setSortValue(item.sortOrder);
                setEditingSort(false);
              }
            }}
            autoFocus
            min={0}
            style={{ width: 60 }}
          />
        ) : (
          <span
            style={{ cursor: "pointer", color: "#666" }}
            onClick={() => setEditingSort(true)}
          >
            {item.sortOrder}
          </span>
        )}
      </Space>
      <Switch
        checked={item.status === 1}
        onChange={async () => {
          const newStatus = item.status === 1 ? 0 : 1;
          try {
            await dictApi.setStatus(item.id, newStatus);
            message.success(newStatus === 1 ? "已启用" : "已停用");
            onReload();
          } catch {
            // 拦截器已处理
          }
        }}
        size="small"
      />
    </List.Item>
  );
}