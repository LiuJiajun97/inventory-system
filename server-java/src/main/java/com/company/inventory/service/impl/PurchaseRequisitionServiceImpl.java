package com.company.inventory.service.impl;

import com.company.inventory.common.constant.RequisitionStatus;
import com.company.inventory.common.exception.BizException;
import com.company.inventory.common.page.PageResult;
import com.company.inventory.common.support.DataScope;
import com.company.inventory.common.support.DateRangeSupport;
import com.company.inventory.common.support.DocStateSupport;
import com.company.inventory.common.util.QtyUtils;
import com.company.inventory.mapper.DictMapper;
import com.company.inventory.mapper.ItemMapper;
import com.company.inventory.mapper.PurchaseRequisitionItemMapper;
import com.company.inventory.mapper.PurchaseRequisitionMapper;
import com.company.inventory.mapper.UserMapper;
import com.company.inventory.mapper.WarehouseMapper;
import com.company.inventory.model.dto.purchase.PurchaseRequisitionCreateDTO;
import com.company.inventory.model.dto.purchase.PurchaseRequisitionLineDTO;
import com.company.inventory.model.entity.dict.DictDO;
import com.company.inventory.model.entity.item.ItemDO;
import com.company.inventory.model.entity.purchase.PurchaseRequisitionDO;
import com.company.inventory.model.entity.purchase.PurchaseRequisitionItemDO;
import com.company.inventory.model.entity.user.UserDO;
import com.company.inventory.model.entity.warehouse.WarehouseDO;
import com.company.inventory.model.query.PurchaseRequisitionQuery;
import com.company.inventory.model.vo.purchase.PurchaseRequisitionItemVO;
import com.company.inventory.model.vo.purchase.PurchaseRequisitionVO;
import com.company.inventory.service.DocNoService;
import com.company.inventory.service.PurchaseRequisitionService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 请购单服务实现(V25,无审批:无金额合计 + 状态机 + 转换触发标记源单)。
 *
 * @author inventory
 */
@Service
public class PurchaseRequisitionServiceImpl implements PurchaseRequisitionService {

    /** 日志。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(PurchaseRequisitionServiceImpl.class);

    /** 单据中文名(错误提示用)。 */
    private static final String DOC_NAME = "请购单";

    /** 字典 dept 类型编码。 */
    private static final String DEPT_DICT_TYPE = "dept";

    /** 表头 Mapper。 */
    private final PurchaseRequisitionMapper requisitionMapper;
    /** 行 Mapper。 */
    private final PurchaseRequisitionItemMapper itemMapper;
    /** 物品 Mapper。 */
    private final ItemMapper itemMasterMapper;
    /** 用户 Mapper。 */
    private final UserMapper userMapper;
    /** 仓库 Mapper。 */
    private final WarehouseMapper warehouseMapper;
    /** 字典 Mapper(申请部门 dept 类型校验)。 */
    private final DictMapper dictMapper;
    /** 单据号服务。 */
    private final DocNoService docNoService;
    /** 状态机支撑。 */
    private final DocStateSupport stateSupport;

    /**
     * 构造服务。
     *
     * @param requisitionMapper 表头 Mapper
     * @param itemMapper        行 Mapper
     * @param itemMasterMapper  物品 Mapper
     * @param userMapper        用户 Mapper
     * @param warehouseMapper   仓库 Mapper
     * @param dictMapper        字典 Mapper
     * @param docNoService      单据号服务
     * @param stateSupport      状态机支撑
     */
    public PurchaseRequisitionServiceImpl(PurchaseRequisitionMapper requisitionMapper,
            PurchaseRequisitionItemMapper itemMapper, ItemMapper itemMasterMapper,
            UserMapper userMapper, WarehouseMapper warehouseMapper, DictMapper dictMapper,
            DocNoService docNoService, DocStateSupport stateSupport) {
        this.requisitionMapper = requisitionMapper;
        this.itemMapper = itemMapper;
        this.itemMasterMapper = itemMasterMapper;
        this.userMapper = userMapper;
        this.warehouseMapper = warehouseMapper;
        this.dictMapper = dictMapper;
        this.docNoService = docNoService;
        this.stateSupport = stateSupport;
    }

    /**
     * 新建请购单(draft)。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PurchaseRequisitionVO create(PurchaseRequisitionCreateDTO dto, String username) {
        requireWarehouse(dto.warehouseId());
        requireUser(dto.applicantId());
        requireDepartment(dto.department());
        PurchaseRequisitionDO req = new PurchaseRequisitionDO();
        req.setDocNo(docNoService.generatePurchaseRequisitionNo());
        req.setDocDate(dto.docDate());
        req.setWarehouseId(dto.warehouseId());
        req.setApplicantId(dto.applicantId());
        req.setDepartment(dto.department());
        req.setStatus(RequisitionStatus.DRAFT);
        req.setRemark(dto.remark());
        req.setCreator(username);
        req.setCreatedAt(LocalDateTime.now());
        requisitionMapper.insert(req);
        applyLines(req, dto);
        LOGGER.info("新建请购单: docNo={}, warehouseId={}, 行数={}, operator={}",
                req.getDocNo(), dto.warehouseId(), dto.items().size(), username);
        return get(req.getId());
    }

    /**
     * 编辑请购单(仅 draft)。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PurchaseRequisitionVO update(long id, PurchaseRequisitionCreateDTO dto, String username) {
        PurchaseRequisitionDO req = requireRequisition(id);
        if (!RequisitionStatus.DRAFT.equals(req.getStatus())) {
            throw new BizException(DOC_NAME + "仅草稿状态可编辑");
        }
        requireWarehouse(dto.warehouseId());
        requireUser(dto.applicantId());
        requireDepartment(dto.department());
        req.setDocDate(dto.docDate());
        req.setWarehouseId(dto.warehouseId());
        req.setApplicantId(dto.applicantId());
        req.setDepartment(dto.department());
        req.setRemark(dto.remark());
        req.setUpdater(username);
        req.setUpdatedAt(LocalDateTime.now());
        itemMapper.delete(new LambdaQueryWrapper<PurchaseRequisitionItemDO>()
                .eq(PurchaseRequisitionItemDO::getRequisitionId, id));
        applyLines(req, dto);
        requisitionMapper.updateById(req);
        LOGGER.info("编辑请购单: id={}, docNo={}, operator={}", id, req.getDocNo(), username);
        return get(id);
    }

    /**
     * 提交请购单(draft → submitted)。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PurchaseRequisitionVO submit(long id, String username) {
        PurchaseRequisitionDO req = requireRequisition(id);
        if (!RequisitionStatus.DRAFT.equals(req.getStatus())) {
            throw new BizException(DOC_NAME + "仅草稿状态可提交");
        }
        int n = stateSupport.transition(requisitionMapper, id,
                List.of(RequisitionStatus.DRAFT), RequisitionStatus.SUBMITTED, username);
        if (n == 0) {
            throw new BizException(DOC_NAME + "状态已变更,请刷新后重试");
        }
        return get(id);
    }

    /**
     * 取消请购单(draft/submitted → cancelled)。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PurchaseRequisitionVO cancel(long id, String username) {
        PurchaseRequisitionDO req = requireRequisition(id);
        stateSupport.assertWritable(req.getStatus(), DOC_NAME);
        int n = stateSupport.transition(requisitionMapper, id,
                List.of(RequisitionStatus.DRAFT, RequisitionStatus.SUBMITTED),
                RequisitionStatus.CANCELLED, username);
        if (n == 0) {
            throw new BizException(DOC_NAME + "状态已变更,请刷新后重试");
        }
        return get(id);
    }

    /**
     * 请购单详情。
     */
    @Override
    public PurchaseRequisitionVO get(long id) {
        PurchaseRequisitionDO req = requireRequisition(id);
        return toVO(req);
    }

    /**
     * 请购单分页列表。
     */
    @Override
    public PageResult<PurchaseRequisitionVO> list(PurchaseRequisitionQuery query) {
        // 数据权限:未授权用户查空;授权用户只看收货仓在授权仓内的请购单(admin 豁免不过滤)
        List<Long> allowed = DataScope.allowedWarehouseIds();
        if (allowed != null && allowed.isEmpty()) {
            return PageResult.of(List.of(), 0L, query.getPage(), query.getPageSize());
        }
        LambdaQueryWrapper<PurchaseRequisitionDO> wrapper = new LambdaQueryWrapper<>();
        if (allowed != null) {
            wrapper.in(PurchaseRequisitionDO::getWarehouseId, allowed);
        }
        if (query.getWarehouseId() != null) {
            wrapper.eq(PurchaseRequisitionDO::getWarehouseId, query.getWarehouseId());
        }
        if (query.getApplicantId() != null) {
            wrapper.eq(PurchaseRequisitionDO::getApplicantId, query.getApplicantId());
        }
        if (StringUtils.hasText(query.getDocNo())) {
            wrapper.like(PurchaseRequisitionDO::getDocNo, query.getDocNo().trim());
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(PurchaseRequisitionDO::getStatus, query.getStatus().trim());
        }
        LocalDate from = DateRangeSupport.parseDate(query.getFrom(), "日期起");
        if (from != null) {
            wrapper.ge(PurchaseRequisitionDO::getDocDate, from);
        }
        LocalDate to = DateRangeSupport.parseDate(query.getTo(), "日期止");
        if (to != null) {
            wrapper.le(PurchaseRequisitionDO::getDocDate, to);
        }
        wrapper.orderByDesc(PurchaseRequisitionDO::getId);
        Page<PurchaseRequisitionDO> page = requisitionMapper.selectPage(
                Page.of(query.getPage(), query.getPageSize()), wrapper);
        List<PurchaseRequisitionVO> vos = page.getRecords().stream()
                .map(this::toVO).collect(Collectors.toList());
        return PageResult.of(vos, page.getTotal(), query.getPage(), query.getPageSize());
    }

    /**
     * 行落库(无金额合计,行价可空不汇总)。
     *
     * @param req 请购单头(必须已 insert,有 ID)
     * @param dto 入参
     */
    private void applyLines(PurchaseRequisitionDO req, PurchaseRequisitionCreateDTO dto) {
        Set<Long> itemIds = new HashSet<>();
        for (PurchaseRequisitionLineDTO line : dto.items()) {
            itemIds.add(line.itemId());
        }
        List<ItemDO> items = itemIds.isEmpty() ? List.of() : itemMasterMapper.selectByIds(itemIds);
        Map<Long, ItemDO> itemMap = new HashMap<>();
        for (ItemDO it : items) {
            itemMap.put(it.getId(), it);
        }
        for (int i = 0; i < dto.items().size(); i++) {
            PurchaseRequisitionLineDTO line = dto.items().get(i);
            ItemDO item = itemMap.get(line.itemId());
            if (item == null) {
                throw new BizException("物品不存在: id=" + line.itemId());
            }
            PurchaseRequisitionItemDO ri = new PurchaseRequisitionItemDO();
            ri.setRequisitionId(req.getId());
            ri.setLineNo(i + 1);
            ri.setItemId(line.itemId());
            ri.setQuantity(line.quantity());
            ri.setExpectedDate(line.expectedDate());
            ri.setUnitPrice(line.unitPrice());
            ri.setRemark(line.remark());
            itemMapper.insert(ri);
        }
    }

    /**
     * 校验仓库存在。
     *
     * @param warehouseId 仓库 ID
     */
    private void requireWarehouse(long warehouseId) {
        WarehouseDO warehouse = warehouseMapper.selectById(warehouseId);
        if (warehouse == null) {
            throw new BizException("收货仓库不存在: id=" + warehouseId);
        }
    }

    /**
     * 校验用户存在(申请人)。
     *
     * @param userId 用户 ID
     */
    private void requireUser(long userId) {
        UserDO user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException("申请人不存在: id=" + userId);
        }
    }

    /**
     * 校验申请部门字典项存在且启用(字典 dept 类型,可空)。
     *
     * @param department 字典 dept 类型的 dict_key
     */
    private void requireDepartment(String department) {
        if (!StringUtils.hasText(department)) {
            return;
        }
        DictDO dict = dictMapper.selectOne(new LambdaQueryWrapper<DictDO>()
                .eq(DictDO::getDictType, DEPT_DICT_TYPE)
                .eq(DictDO::getDictKey, department)
                .eq(DictDO::getStatus, 1));
        if (dict == null) {
            throw new BizException("申请部门字典项不存在或已停用: " + department);
        }
    }

    /**
     * 查请购单,不存在抛 404。
     *
     * @param id 请购单 ID
     * @return 请购单
     */
    private PurchaseRequisitionDO requireRequisition(long id) {
        PurchaseRequisitionDO req = requisitionMapper.selectById(id);
        if (req == null) {
            throw BizException.notFound("请购单不存在");
        }
        assertWarehouseAccess(req.getWarehouseId());
        return req;
    }

    /**
     * 校验当前用户对收货仓的数据权限:非 admin 且收货仓不在授权仓内 → 403(admin 豁免)。
     *
     * @param warehouseId 收货仓 ID
     */
    private void assertWarehouseAccess(Long warehouseId) {
        List<Long> allowed = DataScope.allowedWarehouseIds();
        if (allowed == null) {
            return;
        }
        if (!allowed.contains(warehouseId)) {
            throw BizException.forbidden("无权操作该仓库的单据");
        }
    }

    /**
     * DO 转 VO(详情用,含行)。
     *
     * @param req 请购单头
     * @return VO
     */
    private PurchaseRequisitionVO toVO(PurchaseRequisitionDO req) {
        List<PurchaseRequisitionItemDO> lines = itemMapper.selectList(
                new LambdaQueryWrapper<PurchaseRequisitionItemDO>()
                        .eq(PurchaseRequisitionItemDO::getRequisitionId, req.getId())
                        .orderByAsc(PurchaseRequisitionItemDO::getLineNo));
        Set<Long> itemIds = new HashSet<>();
        for (PurchaseRequisitionItemDO line : lines) {
            itemIds.add(line.getItemId());
        }
        Map<Long, ItemDO> itemMap = new HashMap<>();
        if (!itemIds.isEmpty()) {
            for (ItemDO it : itemMasterMapper.selectByIds(itemIds)) {
                itemMap.put(it.getId(), it);
            }
        }
        List<PurchaseRequisitionItemVO> lineVos = new ArrayList<>();
        for (PurchaseRequisitionItemDO line : lines) {
            ItemDO item = itemMap.get(line.getItemId());
            lineVos.add(new PurchaseRequisitionItemVO(line.getId(), line.getLineNo(),
                    line.getItemId(),
                    item == null ? null : item.getItemCode(),
                    item == null ? null : item.getItemName(),
                    item == null ? null : item.getUnit(),
                    QtyUtils.toContractString(line.getQuantity()),
                    line.getExpectedDate(), line.getUnitPrice(),
                    line.getRemark()));
        }
        return new PurchaseRequisitionVO(req.getId(), req.getDocNo(),
                req.getDocDate(), req.getWarehouseId(), req.getApplicantId(),
                req.getDepartment(), req.getStatus(),
                req.getCreator(), req.getCreatedAt(),
                req.getUpdater(), req.getUpdatedAt(), req.getRemark(),
                lineVos);
    }
}