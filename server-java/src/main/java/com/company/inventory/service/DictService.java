package com.company.inventory.service;

import com.company.inventory.model.vo.dict.DictOptionVO;

import java.util.List;

/**
 * 字典服务接口。
 *
 * @author inventory
 */
public interface DictService {

    /**
     * 按类型查启用字典项(所有角色可读,只取 status=1,按 sortOrder 升序、dictKey 升序)。
     *
     * @param dictType 字典类型(如 warehouseType)
     * @return 字典项列表(code/label)
     */
    List<DictOptionVO> listByType(String dictType);
}
