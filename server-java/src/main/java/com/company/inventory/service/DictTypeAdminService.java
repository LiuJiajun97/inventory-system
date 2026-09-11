package com.company.inventory.service;

import com.company.inventory.dto.dict.DictTypeCreateDTO;
import com.company.inventory.dto.dict.DictTypeUpdateDTO;
import com.company.inventory.vo.dict.DictTypeVO;

import java.util.List;

/**
 * 字典类型管理服务接口。
 *
 * @author inventory
 */
public interface DictTypeAdminService {

    /**
     * 查询全部字典类型(含停用,带启用项数)。
     *
     * @return 类型列表
     */
    List<DictTypeVO> listAll();

    /**
     * 新建字典类型(typeCode 重复时 400)。
     *
     * @param dto 入参
     * @return 新建类型
     */
    DictTypeVO create(DictTypeCreateDTO dto);

    /**
     * 编辑字典类型(改 typeName/remark/status,typeCode 不可改)。
     *
     * @param typeCode 类型编码
     * @param dto      入参
     * @return 更新后类型
     */
    DictTypeVO update(String typeCode, DictTypeUpdateDTO dto);
}
