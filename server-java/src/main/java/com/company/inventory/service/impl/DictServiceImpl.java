package com.company.inventory.service.impl;

import com.company.inventory.model.entity.dict.DictDO;
import com.company.inventory.mapper.DictMapper;
import com.company.inventory.service.DictService;
import com.company.inventory.model.vo.dict.DictOptionVO;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 字典服务实现:LambdaQueryWrapper 查询,不手写 SQL。
 *
 * @author inventory
 */
@Service
public class DictServiceImpl implements DictService {

    /** 启用状态。 */
    private static final int STATUS_ENABLED = 1;

    /** 字典 Mapper。 */
    private final DictMapper dictMapper;

    /**
     * 构造服务。
     *
     * @param dictMapper 字典 Mapper
     */
    public DictServiceImpl(DictMapper dictMapper) {
        this.dictMapper = dictMapper;
    }

    /**
     * 按类型查启用字典项(status=1,sortOrder 升序、dictKey 升序)。
     *
     * @param dictType 字典类型
     * @return 字典项列表
     */
    @Override
    public List<DictOptionVO> listByType(String dictType) {
        LambdaQueryWrapper<DictDO> wrapper = new LambdaQueryWrapper<DictDO>()
                .eq(DictDO::getDictType, dictType)
                .eq(DictDO::getStatus, STATUS_ENABLED)
                .orderByAsc(DictDO::getSortOrder)
                .orderByAsc(DictDO::getDictKey);
        return dictMapper.selectList(wrapper).stream()
                .map(d -> new DictOptionVO(d.getDictKey(), d.getDictLabel()))
                .toList();
    }
}
