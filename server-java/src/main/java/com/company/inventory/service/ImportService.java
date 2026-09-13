package com.company.inventory.service;

import com.company.inventory.model.vo.excel.ImportResultVO;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * 导入服务:物品/供应商/客户 xlsx 批量导入 + 模板下载。
 *
 * <p>导入规则:逐行校验(必填/数值/字典映射),成功行走既有 Service.create
 * (复用编码查重/条码查重/审计填充,行级独立事务),失败行汇总进
 * {@link ImportResultVO#getFailed()} 不阻断其他行。</p>
 *
 * @author inventory
 */
public interface ImportService {

    /**
     * 物品导入(逐行校验 + 走 ItemService.create)。
     *
     * @param file xlsx 文件(列头须与模板一致)
     * @return 导入结果(imported + 失败行汇总)
     * @throws com.company.inventory.common.exception.BizException 非 xlsx / 解析失败
     */
    ImportResultVO importItems(MultipartFile file);

    /**
     * 供应商导入(逐行校验 + 走 SupplierService.create)。
     *
     * @param file     xlsx 文件
     * @param username 当前登录用户名(审计填充)
     * @return 导入结果
     * @throws com.company.inventory.common.exception.BizException 非 xlsx / 解析失败
     */
    ImportResultVO importSuppliers(MultipartFile file, String username);

    /**
     * 客户导入(逐行校验 + 走 CustomerService.create)。
     *
     * @param file     xlsx 文件
     * @param username 当前登录用户名(审计填充)
     * @return 导入结果
     * @throws com.company.inventory.common.exception.BizException 非 xlsx / 解析失败
     */
    ImportResultVO importCustomers(MultipartFile file, String username);

    /**
     * 物品导入模板下载(表头 + 1 行示例数据)。
     *
     * @param resp HTTP 响应
     */
    void writeItemTemplate(HttpServletResponse resp);

    /**
     * 供应商导入模板下载(表头 + 1 行示例数据)。
     *
     * @param resp HTTP 响应
     */
    void writeSupplierTemplate(HttpServletResponse resp);

    /**
     * 客户导入模板下载(表头 + 1 行示例数据)。
     *
     * @param resp HTTP 响应
     */
    void writeCustomerTemplate(HttpServletResponse resp);
}
