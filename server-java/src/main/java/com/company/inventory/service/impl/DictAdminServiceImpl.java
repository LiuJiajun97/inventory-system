package com.company.inventory.service.impl;

import com.company.inventory.common.constant.ErrorCode;
import com.company.inventory.common.exception.BizException;
import com.company.inventory.model.dto.dict.DictCreateDTO;
import com.company.inventory.model.dto.dict.DictUpdateDTO;
import com.company.inventory.model.entity.dict.DictDO;
import com.company.inventory.mapper.DictMapper;
import com.company.inventory.service.DictAdminService;
import com.company.inventory.support.DictReferenceRegistry;
import com.company.inventory.model.vo.dict.DictVO;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 字典管理服务实现。
 *
 * @author inventory
 */
@Service
public class DictAdminServiceImpl implements DictAdminService {

    /** 日志。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(DictAdminServiceImpl.class);

    /** 启用状态。 */
    private static final int STATUS_ENABLED = 1;

    /** 字典 Mapper。 */
    private final DictMapper dictMapper;

    /** 引用校验注册表。 */
    private final DictReferenceRegistry registry;

    /**
     * 构造服务。
     *
     * @param dictMapper 字典 Mapper
     * @param registry   引用校验注册表
     */
    public DictAdminServiceImpl(DictMapper dictMapper, DictReferenceRegistry registry) {
        this.dictMapper = dictMapper;
        this.registry = registry;
    }

    /**
     * 查询指定类型的全部字典项(含停用项,按 sortOrder 升序、dictKey 升序)。
     *
     * @param dictType 字典类型
     * @return 字典项列表
     */
    @Override
    public List<DictVO> listAllByType(String dictType) {
        LambdaQueryWrapper<DictDO> wrapper = new LambdaQueryWrapper<DictDO>()
                .eq(DictDO::getDictType, dictType)
                .orderByAsc(DictDO::getSortOrder)
                .orderByAsc(DictDO::getDictKey);
        return dictMapper.selectList(wrapper).stream()
                .map(this::toVO)
                .toList();
    }

    /**
     * 新建字典项(dictType+dictKey 冲突时 400)。
     *
     * @param dto 入参
     * @return 新建字典项
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DictVO create(DictCreateDTO dto) {
        Long exist = dictMapper.selectCount(new LambdaQueryWrapper<DictDO>()
                .eq(DictDO::getDictType, dto.dictType())
                .eq(DictDO::getDictKey, dto.dictKey()));
        if (exist != null && exist > 0) {
            throw new BizException("字典项已存在: " + dto.dictKey(),
                    ErrorCode.BIZ_ERROR, ErrorCode.HTTP_BAD_REQUEST);
        }
        DictDO d = new DictDO();
        d.setDictType(dto.dictType());
        d.setDictKey(dto.dictKey());
        d.setDictLabel(dto.dictLabel());
        d.setSortOrder(dto.sortOrder() == null ? 0 : dto.sortOrder());
        d.setStatus(dto.status() == null ? STATUS_ENABLED : dto.status());
        dictMapper.insert(d);
        LOGGER.info("新建字典项: type={}, key={}, label={}", dto.dictType(),
                dto.dictKey(), dto.dictLabel());
        return toVO(d);
    }

    /**
     * 编辑字典项(仅 dictLabel/sortOrder 可改,dictType/dictKey 不可改)。
     *
     * @param id  字典项 ID
     * @param dto 入参
     * @return 更新后字典项
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DictVO update(long id, DictUpdateDTO dto) {
        DictDO d = dictMapper.selectById(id);
        if (d == null) {
            throw BizException.notFound("字典项不存在");
        }
        if (dto.dictLabel() != null) {
            d.setDictLabel(dto.dictLabel());
        }
        if (dto.sortOrder() != null) {
            d.setSortOrder(dto.sortOrder());
        }
        dictMapper.updateById(d);
        LOGGER.info("编辑字典项: id={}, type={}, key={}", id, d.getDictType(), d.getDictKey());
        return toVO(d);
    }

    /**
     * 启用/停用字典项(停用前通过注册表校验是否被引用)。
     *
     * @param id     字典项 ID
     * @param status 目标状态:1 启用/0 停用
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(long id, int status) {
        DictDO d = dictMapper.selectById(id);
        if (d == null) {
            throw BizException.notFound("字典项不存在");
        }
        if (status == 0) {
            String displayName = registry.getRefDisplayName(d.getDictType(), d.getDictKey());
            if (displayName != null) {
                throw new BizException("字典项已被" + displayName + "引用,不能停用",
                        ErrorCode.BIZ_ERROR, ErrorCode.HTTP_BAD_REQUEST);
            }
        }
        d.setStatus(status);
        dictMapper.updateById(d);
        LOGGER.info("更新字典项状态: id={}, type={}, key={}, status={}",
                id, d.getDictType(), d.getDictKey(), status);
    }

    /**
     * 实体转 VO。
     *
     * @param d 实体
     * @return VO
     */
    private DictVO toVO(DictDO d) {
        return new DictVO(d.getId(), d.getDictType(), d.getDictKey(),
                d.getDictLabel(), d.getSortOrder(), d.getStatus());
    }
}
