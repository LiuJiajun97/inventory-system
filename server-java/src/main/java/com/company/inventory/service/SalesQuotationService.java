package com.company.inventory.service;

import com.company.inventory.common.page.PageResult;
import com.company.inventory.model.dto.sales.SalesQuotationCreateDTO;
import com.company.inventory.model.query.SalesQuotationQuery;
import com.company.inventory.model.vo.sales.SalesQuotationVO;

/**
 * 销售报价单服务接口(V25,无审批:draft/sent/converted/voided)。
 *
 * @author inventory
 */
public interface SalesQuotationService {

    /**
     * 新建报价单(草稿)。
     *
     * @param dto      入参
     * @param username 当前操作人
     * @return 报价单 VO
     */
    SalesQuotationVO create(SalesQuotationCreateDTO dto, String username);

    /**
     * 编辑报价单(仅 draft)。
     *
     * @param id       报价单 ID
     * @param dto      入参
     * @param username 当前操作人
     * @return 报价单 VO
     */
    SalesQuotationVO update(long id, SalesQuotationCreateDTO dto, String username);

    /**
     * 标记已发送(draft → sent)。
     *
     * @param id       报价单 ID
     * @param username 当前操作人
     * @return 报价单 VO
     */
    SalesQuotationVO markSent(long id, String username);

    /**
     * 作废报价单(draft/sent → voided)。
     *
     * @param id       报价单 ID
     * @param username 当前操作人
     * @return 报价单 VO
     */
    SalesQuotationVO voidDoc(long id, String username);

    /**
     * 报价单详情。
     *
     * @param id 报价单 ID
     * @return 报价单 VO
     */
    SalesQuotationVO get(long id);

    /**
     * 报价单分页列表。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<SalesQuotationVO> list(SalesQuotationQuery query);
}