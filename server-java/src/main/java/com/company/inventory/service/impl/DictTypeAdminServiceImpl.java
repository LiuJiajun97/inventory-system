package com.company.inventory.service.impl;

import com.company.inventory.common.constant.ErrorCode;
import com.company.inventory.common.exception.BizException;
import com.company.inventory.dto.dict.DictTypeCreateDTO;
import com.company.inventory.dto.dict.DictTypeUpdateDTO;
import com.company.inventory.entity.dict.DictDO;
import com.company.inventory.entity.dict.DictTypeDO;
import com.company.inventory.mapper.DictMapper;
import com.company.inventory.mapper.DictTypeMapper;
import com.company.inventory.service.DictTypeAdminService;
import com.company.inventory.support.DictReferenceRegistry;
import com.company.inventory.vo.dict.DictTypeVO;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 字典类型管理服务实现。
 *
 * @author inventory
 */
@Service
public class DictTypeAdminServiceImpl implements DictTypeAdminService {

    /** 日志。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(DictTypeAdminServiceImpl.class);

    /** 启用状态。 */
    private static final int STATUS_ENABLED = 1;

    /** typeCode 格式:小写字母开头,只含小写字母/数字/下划线。 */
    private static final Pattern TYPE_CODE_PATTERN = Pattern.compile("^[a-z][a-z0-9_]*$");

    /** 字典类型 Mapper。 */
    private final DictTypeMapper dictTypeMapper;

    /** 字典 Mapper(查启用项数用)。 */
    private final DictMapper dictMapper;

    /** 引用校验注册表。 */
    private final DictReferenceRegistry registry;

    /**
     * 构造服务。
     *
     * @param dictTypeMapper 字典类型 Mapper
     * @param dictMapper     字典 Mapper
     * @param registry       引用校验注册表
     */
    public DictTypeAdminServiceImpl(DictTypeMapper dictTypeMapper,
                                    DictMapper dictMapper,
                                    DictReferenceRegistry registry) {
        this.dictTypeMapper = dictTypeMapper;
        this.dictMapper = dictMapper;
        this.registry = registry;
    }

    /**
     * 查询全部字典类型(含停用,按 typeCode 升序)。
     *
     * @return 类型列表(含 enabledCount)
     */
    @Override
    public List<DictTypeVO> listAll() {
        List<DictTypeDO> types = dictTypeMapper.selectList(
                new LambdaQueryWrapper<DictTypeDO>()
                        .orderByAsc(DictTypeDO::getTypeCode));
        return types.stream().map(t -> {
            Long enabledCount = dictMapper.selectCount(
                    new LambdaQueryWrapper<DictDO>()
                            .eq(DictDO::getDictType, t.getTypeCode())
                            .eq(DictDO::getStatus, STATUS_ENABLED));
            return toVO(t, enabledCount);
        }).toList();
    }

    /**
     * 新建字典类型(typeCode 重复时 400)。
     *
     * @param dto 入参
     * @return 新建类型
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DictTypeVO create(DictTypeCreateDTO dto) {
        if (!TYPE_CODE_PATTERN.matcher(dto.typeCode()).matches()) {
            throw new BizException("类型编码只能含小写字母、数字、下划线,且以字母开头",
                    ErrorCode.BIZ_ERROR, ErrorCode.HTTP_BAD_REQUEST);
        }
        Long exist = dictTypeMapper.selectCount(
                new LambdaQueryWrapper<DictTypeDO>()
                        .eq(DictTypeDO::getTypeCode, dto.typeCode()));
        if (exist != null && exist > 0) {
            throw new BizException("类型编码已存在: " + dto.typeCode(),
                    ErrorCode.BIZ_ERROR, ErrorCode.HTTP_BAD_REQUEST);
        }
        DictTypeDO d = new DictTypeDO();
        d.setTypeCode(dto.typeCode());
        d.setTypeName(dto.typeName());
        d.setRemark(dto.remark() == null ? "" : dto.remark());
        d.setStatus(STATUS_ENABLED);
        LocalDateTime now = LocalDateTime.now();
        d.setCreatedAt(now);
        d.setUpdatedAt(now);
        dictTypeMapper.insert(d);
        LOGGER.info("新建字典类型: code={}, name={}", dto.typeCode(), dto.typeName());
        return toVO(d, 0L);
    }

    /**
     * 编辑字典类型(改 typeName/remark/status,typeCode 不可改)。
     *
     * <p>停用前校验:类型下有启用项则拒绝;被业务表引用则拒绝。</p>
     *
     * @param typeCode 类型编码
     * @param dto      入参
     * @return 更新后类型
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DictTypeVO update(String typeCode, DictTypeUpdateDTO dto) {
        DictTypeDO d = dictTypeMapper.selectOne(
                new LambdaQueryWrapper<DictTypeDO>()
                        .eq(DictTypeDO::getTypeCode, typeCode));
        if (d == null) {
            throw BizException.notFound("字典类型不存在");
        }
        if (dto.typeName() != null) {
            d.setTypeName(dto.typeName());
        }
        if (dto.remark() != null) {
            d.setRemark(dto.remark());
        }
        if (dto.status() != null && dto.status() == 0 && d.getStatus() == STATUS_ENABLED) {
            Long enabledCount = dictMapper.selectCount(
                    new LambdaQueryWrapper<DictDO>()
                            .eq(DictDO::getDictType, typeCode)
                            .eq(DictDO::getStatus, STATUS_ENABLED));
            if (enabledCount != null && enabledCount > 0) {
                throw new BizException("该类型下还有启用项,不能停用",
                        ErrorCode.BIZ_ERROR, ErrorCode.HTTP_BAD_REQUEST);
            }
            if (registry.isTypeReferenced(typeCode)) {
                throw new BizException("该字典类型已被业务表引用,不能停用",
                        ErrorCode.BIZ_ERROR, ErrorCode.HTTP_BAD_REQUEST);
            }
        }
        if (dto.status() != null) {
            d.setStatus(dto.status());
        }
        d.setUpdatedAt(LocalDateTime.now());
        dictTypeMapper.updateById(d);
        Long enabledCount = dictMapper.selectCount(
                new LambdaQueryWrapper<DictDO>()
                        .eq(DictDO::getDictType, typeCode)
                        .eq(DictDO::getStatus, STATUS_ENABLED));
        LOGGER.info("编辑字典类型: code={}, name={}", typeCode, d.getTypeName());
        return toVO(d, enabledCount);
    }

    /**
     * 实体转 VO(含启用项数)。
     *
     * @param d            实体
     * @param enabledCount 启用项数
     * @return VO
     */
    private DictTypeVO toVO(DictTypeDO d, Long enabledCount) {
        return new DictTypeVO(d.getId(), d.getTypeCode(), d.getTypeName(),
                d.getRemark(), d.getStatus(), enabledCount);
    }
}
