package com.company.inventory.service;

import com.company.inventory.model.dto.dict.DictCreateDTO;
import com.company.inventory.model.dto.dict.DictUpdateDTO;
import com.company.inventory.model.vo.dict.DictVO;

import java.util.List;

/**
 * 字典管理服务接口(admin 可建可改可停用)。
 *
 * @author inventory
 */
public interface DictAdminService {

    /**
     * 查询指定类型的全部字典项(含停用,管理界面用)。
     *
     * @param dictType 字典类型
     * @return 字典项列表
     */
    List<DictVO> listAllByType(String dictType);

    /**
     * 新建字典项(dictType+dictKey 冲突时抛异常)。
     *
     * @param dto 入参
     * @return 新建字典项
     */
    DictVO create(DictCreateDTO dto);

    /**
     * 编辑字典项(仅 dictLabel/sortOrder 可改)。
     *
     * @param id  字典项 ID
     * @param dto 入参
     * @return 更新后字典项
     */
    DictVO update(long id, DictUpdateDTO dto);

    /**
     * 启用/停用字典项(停用前校验是否被引用)。
     *
     * @param id     字典项 ID
     * @param status 目标状态:1 启用/0 停用
     */
    void updateStatus(long id, int status);
}
