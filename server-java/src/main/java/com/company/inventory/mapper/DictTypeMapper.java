package com.company.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.inventory.model.entity.dict.DictTypeDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 字典类型 Mapper。
 *
 * @author inventory
 */
@Mapper
public interface DictTypeMapper extends BaseMapper<DictTypeDO> {
}
