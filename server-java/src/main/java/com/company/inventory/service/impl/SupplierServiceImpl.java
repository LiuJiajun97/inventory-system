package com.company.inventory.service.impl;

import com.company.inventory.common.constant.ErrorCode;
import com.company.inventory.common.exception.BizException;
import com.company.inventory.common.page.PageResult;
import com.company.inventory.model.dto.supplier.SupplierCreateDTO;
import com.company.inventory.model.dto.supplier.SupplierUpdateDTO;
import com.company.inventory.model.entity.supplier.SupplierDO;
import com.company.inventory.mapper.SupplierMapper;
import com.company.inventory.model.query.SupplierQuery;
import com.company.inventory.service.SupplierService;
import com.company.inventory.model.vo.supplier.SupplierVO;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 供应商主数据服务实现。
 *
 * @author inventory
 */
@Service
public class SupplierServiceImpl implements SupplierService {

    /** 日志。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(SupplierServiceImpl.class);

    /** 默认税率(百分数,13%)。 */
    private static final BigDecimal DEFAULT_TAX_RATE = new BigDecimal("13.00");

    /** 供应商 Mapper。 */
    private final SupplierMapper supplierMapper;

    /**
     * 构造服务。
     *
     * @param supplierMapper 供应商 Mapper
     */
    public SupplierServiceImpl(SupplierMapper supplierMapper) {
        this.supplierMapper = supplierMapper;
    }

    /**
     * 供应商分页列表(按 id 升序,keyword 对编码/名称模糊)。
     *
     * @param query 查询条件(keyword/page/pageSize)
     * @return 分页结果
     */
    @Override
    public PageResult<SupplierVO> list(SupplierQuery query) {
        LambdaQueryWrapper<SupplierDO> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            String like = query.getKeyword().trim();
            wrapper.and(w -> w.like(SupplierDO::getSupplierCode, like)
                    .or().like(SupplierDO::getSupplierName, like));
        }
        wrapper.orderByAsc(SupplierDO::getId);
        Page<SupplierDO> page = supplierMapper.selectPage(
                Page.of(query.getPage(), query.getPageSize()), wrapper);
        List<SupplierVO> vos = page.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(vos, page.getTotal(), query.getPage(), query.getPageSize());
    }

    /**
     * 供应商详情。
     *
     * @param id 供应商 ID
     * @return 供应商
     */
    @Override
    public SupplierVO get(long id) {
        SupplierDO supplier = supplierMapper.selectById(id);
        if (supplier == null) {
            throw BizException.notFound("供应商不存在");
        }
        return toVO(supplier);
    }

    /**
     * 新建供应商。
     *
     * @param dto      入参
     * @param username 当前登录用户名
     * @return 新建供应商
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SupplierVO create(SupplierCreateDTO dto, String username) {
        Long exist = supplierMapper.selectCount(new LambdaQueryWrapper<SupplierDO>()
                .eq(SupplierDO::getSupplierCode, dto.supplierCode()));
        if (exist != null && exist > 0) {
            throw new BizException("供应商编码已存在", ErrorCode.SUPPLIER_CODE_DUP,
                    ErrorCode.HTTP_BAD_REQUEST);
        }
        SupplierDO supplier = new SupplierDO();
        supplier.setSupplierCode(dto.supplierCode());
        supplier.setSupplierName(dto.supplierName());
        supplier.setTaxNo(dto.taxNo());
        supplier.setDefaultTaxRate(
                dto.defaultTaxRate() == null ? DEFAULT_TAX_RATE : dto.defaultTaxRate());
        supplier.setContact(dto.contact());
        supplier.setPhone(dto.phone());
        supplier.setAddress(dto.address());
        supplier.setSettleMethod(dto.settleMethod());
        supplier.setPayTermDays(dto.payTermDays());
        supplier.setBankName(dto.bankName());
        supplier.setBankAccount(dto.bankAccount());
        supplier.setCreditLimit(dto.creditLimit());
        supplier.setDeliveryAddress(dto.deliveryAddress());
        supplier.setEmail(dto.email());
        supplier.setRemark(dto.remark());
        supplier.setStatus(1);
        supplier.setCreator(username);
        supplier.setCreatedAt(LocalDateTime.now());
        supplierMapper.insert(supplier);
        LOGGER.info("新建供应商: code={}, name={}, operator={}",
                dto.supplierCode(), dto.supplierName(), username);
        return toVO(supplier);
    }

    /**
     * 编辑供应商(编码不可改)。
     *
     * @param id       供应商 ID
     * @param dto      入参
     * @param username 当前登录用户名
     * @return 更新后的供应商
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SupplierVO update(long id, SupplierUpdateDTO dto, String username) {
        SupplierDO supplier = supplierMapper.selectById(id);
        if (supplier == null) {
            throw BizException.notFound("供应商不存在");
        }
        if (dto.supplierName() != null) {
            supplier.setSupplierName(dto.supplierName());
        }
        if (dto.taxNo() != null) {
            supplier.setTaxNo(dto.taxNo());
        }
        if (dto.defaultTaxRate() != null) {
            supplier.setDefaultTaxRate(dto.defaultTaxRate());
        }
        if (dto.contact() != null) {
            supplier.setContact(dto.contact());
        }
        if (dto.phone() != null) {
            supplier.setPhone(dto.phone());
        }
        if (dto.address() != null) {
            supplier.setAddress(dto.address());
        }
        if (dto.settleMethod() != null) {
            supplier.setSettleMethod(dto.settleMethod());
        }
        if (dto.payTermDays() != null) {
            supplier.setPayTermDays(dto.payTermDays());
        }
        if (dto.bankName() != null) {
            supplier.setBankName(dto.bankName());
        }
        if (dto.bankAccount() != null) {
            supplier.setBankAccount(dto.bankAccount());
        }
        if (dto.creditLimit() != null) {
            supplier.setCreditLimit(dto.creditLimit());
        }
        if (dto.deliveryAddress() != null) {
            supplier.setDeliveryAddress(dto.deliveryAddress());
        }
        if (dto.email() != null) {
            supplier.setEmail(dto.email());
        }
        if (dto.status() != null) {
            supplier.setStatus(dto.status());
        }
        if (dto.remark() != null) {
            supplier.setRemark(dto.remark());
        }
        supplier.setUpdater(username);
        supplier.setUpdatedAt(LocalDateTime.now());
        supplierMapper.updateById(supplier);
        LOGGER.info("编辑供应商: id={}, operator={}", id, username);
        return toVO(supplier);
    }

    /**
     * 实体转 VO。
     *
     * @param s 实体
     * @return VO
     */
    private SupplierVO toVO(SupplierDO s) {
        return new SupplierVO(s.getId(), s.getSupplierCode(), s.getSupplierName(), s.getTaxNo(),
                s.getDefaultTaxRate(), s.getContact(), s.getPhone(), s.getAddress(),
                s.getSettleMethod(), s.getPayTermDays(), s.getBankName(), s.getBankAccount(),
                s.getCreditLimit(), s.getDeliveryAddress(), s.getEmail(), s.getStatus(), s.getRemark(),
                s.getCreator(), s.getCreatedAt(), s.getUpdater(), s.getUpdatedAt());
    }
}
