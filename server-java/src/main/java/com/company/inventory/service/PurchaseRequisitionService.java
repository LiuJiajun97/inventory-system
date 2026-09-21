package com.company.inventory.service;

import com.company.inventory.common.page.PageResult;
import com.company.inventory.model.dto.purchase.PurchaseRequisitionCreateDTO;
import com.company.inventory.model.query.PurchaseRequisitionQuery;
import com.company.inventory.model.vo.purchase.PurchaseRequisitionVO;

/**
 * 请购单服务接口(V25,无审批:draft/submitted/converted/cancelled)。
 *
 * @author inventory
 */
public interface PurchaseRequisitionService {

    /**
     * 新建请购单(草稿)。
     *
     * @param dto      入参
     * @param username 当前操作人
     * @return 请购单 VO
     */
    PurchaseRequisitionVO create(PurchaseRequisitionCreateDTO dto, String username);

    /**
     * 编辑请购单(仅 draft)。
     *
     * @param id       请购单 ID
     * @param dto      入参
     * @param username 当前操作人
     * @return 请购单 VO
     */
    PurchaseRequisitionVO update(long id, PurchaseRequisitionCreateDTO dto, String username);

    /**
     * 提交请购单(draft → submitted)。
     *
     * @param id       请购单 ID
     * @param username 当前操作人
     * @return 请购单 VO
     */
    PurchaseRequisitionVO submit(long id, String username);

    /**
     * 取消请购单(draft/submitted → cancelled)。
     *
     * @param id       请购单 ID
     * @param username 当前操作人
     * @return 请购单 VO
     */
    PurchaseRequisitionVO cancel(long id, String username);

    /**
     * 请购单详情。
     *
     * @param id 请购单 ID
     * @return 请购单 VO
     */
    PurchaseRequisitionVO get(long id);

    /**
     * 请购单分页列表。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<PurchaseRequisitionVO> list(PurchaseRequisitionQuery query);
}