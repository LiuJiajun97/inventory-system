package com.company.inventory.service.impl;

import com.company.inventory.common.constant.ErrorCode;
import com.company.inventory.common.exception.BizException;
import com.company.inventory.common.page.PageResult;
import com.company.inventory.model.dto.customer.CustomerCreateDTO;
import com.company.inventory.model.dto.customer.CustomerUpdateDTO;
import com.company.inventory.model.entity.customer.CustomerDO;
import com.company.inventory.mapper.CustomerMapper;
import com.company.inventory.model.query.CustomerQuery;
import com.company.inventory.service.CustomerService;
import com.company.inventory.model.vo.customer.CustomerVO;

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
 * 客户主数据服务实现。
 *
 * @author inventory
 */
@Service
public class CustomerServiceImpl implements CustomerService {

    /** 日志。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(CustomerServiceImpl.class);

    /** 默认税率(百分数,13%)。 */
    private static final BigDecimal DEFAULT_TAX_RATE = new BigDecimal("13.00");

    /** 客户 Mapper。 */
    private final CustomerMapper customerMapper;

    /**
     * 构造服务。
     *
     * @param customerMapper 客户 Mapper
     */
    public CustomerServiceImpl(CustomerMapper customerMapper) {
        this.customerMapper = customerMapper;
    }

    /**
     * 客户分页列表(按 id 升序,keyword 对编码/名称模糊)。
     *
     * @param query 查询条件(keyword/page/pageSize)
     * @return 分页结果
     */
    @Override
    public PageResult<CustomerVO> list(CustomerQuery query) {
        LambdaQueryWrapper<CustomerDO> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            String like = query.getKeyword().trim();
            wrapper.and(w -> w.like(CustomerDO::getCustomerCode, like)
                    .or().like(CustomerDO::getCustomerName, like));
        }
        wrapper.orderByAsc(CustomerDO::getId);
        Page<CustomerDO> page = customerMapper.selectPage(
                Page.of(query.getPage(), query.getPageSize()), wrapper);
        List<CustomerVO> vos = page.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(vos, page.getTotal(), query.getPage(), query.getPageSize());
    }

    /**
     * 客户详情。
     *
     * @param id 客户 ID
     * @return 客户
     */
    @Override
    public CustomerVO get(long id) {
        CustomerDO customer = customerMapper.selectById(id);
        if (customer == null) {
            throw BizException.notFound("客户不存在");
        }
        return toVO(customer);
    }

    /**
     * 新建客户。
     *
     * @param dto      入参
     * @param username 当前登录用户名
     * @return 新建客户
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public CustomerVO create(CustomerCreateDTO dto, String username) {
        Long exist = customerMapper.selectCount(new LambdaQueryWrapper<CustomerDO>()
                .eq(CustomerDO::getCustomerCode, dto.customerCode()));
        if (exist != null && exist > 0) {
            throw new BizException("客户编码已存在", ErrorCode.CUSTOMER_CODE_DUP,
                    ErrorCode.HTTP_BAD_REQUEST);
        }
        CustomerDO customer = new CustomerDO();
        customer.setCustomerCode(dto.customerCode());
        customer.setCustomerName(dto.customerName());
        customer.setTaxNo(dto.taxNo());
        customer.setDefaultTaxRate(
                dto.defaultTaxRate() == null ? DEFAULT_TAX_RATE : dto.defaultTaxRate());
        customer.setContact(dto.contact());
        customer.setPhone(dto.phone());
        customer.setAddress(dto.address());
        customer.setSettleMethod(dto.settleMethod());
        customer.setPayTermDays(dto.payTermDays());
        customer.setBankName(dto.bankName());
        customer.setBankAccount(dto.bankAccount());
        customer.setCreditLimit(dto.creditLimit());
        customer.setDeliveryAddress(dto.deliveryAddress());
        customer.setEmail(dto.email());
        customer.setRemark(dto.remark());
        customer.setStatus(1);
        customer.setCreator(username);
        customer.setCreatedAt(LocalDateTime.now());
        customerMapper.insert(customer);
        LOGGER.info("新建客户: code={}, name={}, operator={}",
                dto.customerCode(), dto.customerName(), username);
        return toVO(customer);
    }

    /**
     * 编辑客户(编码不可改)。
     *
     * @param id       客户 ID
     * @param dto      入参
     * @param username 当前登录用户名
     * @return 更新后的客户
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public CustomerVO update(long id, CustomerUpdateDTO dto, String username) {
        CustomerDO customer = customerMapper.selectById(id);
        if (customer == null) {
            throw BizException.notFound("客户不存在");
        }
        if (dto.customerName() != null) {
            customer.setCustomerName(dto.customerName());
        }
        if (dto.taxNo() != null) {
            customer.setTaxNo(dto.taxNo());
        }
        if (dto.defaultTaxRate() != null) {
            customer.setDefaultTaxRate(dto.defaultTaxRate());
        }
        if (dto.contact() != null) {
            customer.setContact(dto.contact());
        }
        if (dto.phone() != null) {
            customer.setPhone(dto.phone());
        }
        if (dto.address() != null) {
            customer.setAddress(dto.address());
        }
        if (dto.settleMethod() != null) {
            customer.setSettleMethod(dto.settleMethod());
        }
        if (dto.payTermDays() != null) {
            customer.setPayTermDays(dto.payTermDays());
        }
        if (dto.bankName() != null) {
            customer.setBankName(dto.bankName());
        }
        if (dto.bankAccount() != null) {
            customer.setBankAccount(dto.bankAccount());
        }
        if (dto.creditLimit() != null) {
            customer.setCreditLimit(dto.creditLimit());
        }
        if (dto.deliveryAddress() != null) {
            customer.setDeliveryAddress(dto.deliveryAddress());
        }
        if (dto.email() != null) {
            customer.setEmail(dto.email());
        }
        if (dto.status() != null) {
            customer.setStatus(dto.status());
        }
        if (dto.remark() != null) {
            customer.setRemark(dto.remark());
        }
        customer.setUpdater(username);
        customer.setUpdatedAt(LocalDateTime.now());
        customerMapper.updateById(customer);
        LOGGER.info("编辑客户: id={}, operator={}", id, username);
        return toVO(customer);
    }

    /**
     * 实体转 VO。
     *
     * @param s 实体
     * @return VO
     */
    private CustomerVO toVO(CustomerDO s) {
        return new CustomerVO(s.getId(), s.getCustomerCode(), s.getCustomerName(), s.getTaxNo(),
                s.getDefaultTaxRate(), s.getContact(), s.getPhone(), s.getAddress(),
                s.getSettleMethod(), s.getPayTermDays(), s.getBankName(), s.getBankAccount(),
                s.getCreditLimit(), s.getDeliveryAddress(), s.getEmail(), s.getStatus(), s.getRemark(),
                s.getCreator(), s.getCreatedAt(), s.getUpdater(), s.getUpdatedAt());
    }
}
