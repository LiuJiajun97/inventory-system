package com.company.inventory.service;

import com.company.inventory.common.page.PageResult;
import com.company.inventory.model.dto.settlement.InvoiceCreateDTO;
import com.company.inventory.model.dto.settlement.InvoiceUpdateDTO;
import com.company.inventory.model.entity.returns.PurchaseReturnDO;
import com.company.inventory.model.entity.returns.SalesReturnDO;
import com.company.inventory.model.query.InvoiceQuery;
import com.company.inventory.model.vo.settlement.InvoiceVO;
import com.company.inventory.model.vo.settlement.InvoiceableLineVO;

import java.util.List;

/**
 * 发票服务(V18 结算域):手工登记正票 + 退货生成红字凭单(负票)。
 *
 * <p>业务口径:只有 confirmed 发票进应收应付台账;行级匹配 |开票额-源行含税额|>0.01 落
 * mismatch(挂起);防超开硬校验(未作废累计开票额 + 本次 ≤ 源行含税额)。</p>
 *
 * @author inventory
 */
public interface InvoiceService {

    /**
     * 手工新建发票(正票;服务端逐行取源单据行含税额,防超开硬校验)。
     *
     * @param dto      入参(发票头 + 行)
     * @param username 当前登录用户名
     * @return 发票(含行)
     */
    InvoiceVO create(InvoiceCreateDTO dto, String username);

    /**
     * 修改发票(仅 draft/mismatch 可改;行可改/删,正票行可加,负票行不可加)。
     *
     * @param id       发票 ID
     * @param dto      入参(全量行,覆盖式)
     * @param username 当前登录用户名
     * @return 发票(含行)
     */
    InvoiceVO update(long id, InvoiceUpdateDTO dto, String username);

    /**
     * 确认发票(draft/mismatch → confirmed;仍有差异行时拒绝)。
     *
     * @param id       发票 ID
     * @param username 当前登录用户名
     * @return 发票
     */
    InvoiceVO confirm(long id, String username);

    /**
     * 作废发票(非 voided 均可,留痕不物理删,释放已开票额度)。
     *
     * @param id       发票 ID
     * @param username 当前登录用户名
     * @return 发票
     */
    InvoiceVO voidInvoice(long id, String username);

    /**
     * 发票分页列表(数据权限口径与采购订单列表一致)。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<InvoiceVO> list(InvoiceQuery query);

    /**
     * 发票详情(带行与源单据号)。
     *
     * @param id 发票 ID
     * @return 发票
     */
    InvoiceVO get(long id);

    /**
     * 查某对方可挂票的源单据行(未开票/部分开票,新建发票选择区用)。
     *
     * @param invoiceType 发票类型(purchase | sales)
     * @param partyId     对方 ID
     * @return 可挂票行列表
     */
    List<InvoiceableLineVO> invoiceableLines(String invoiceType, long partyId);

    /**
     * 采购退货过账后同事务生成负数草稿发票(红字凭单,对齐 U8 红字发票/金蝶红字应付单)。
     *
     * @param doc      采购退货单(已过账)
     * @param username 当前登录用户名
     * @return 生成的发票 ID
     */
    long generateForPurchaseReturn(PurchaseReturnDO doc, String username);

    /**
     * 销售退货过账后同事务生成负数草稿发票(贷项凭单,核减应收)。
     *
     * @param doc      销售退货单(已过账)
     * @param username 当前登录用户名
     * @return 生成的发票 ID
     */
    long generateForSalesReturn(SalesReturnDO doc, String username);
}
