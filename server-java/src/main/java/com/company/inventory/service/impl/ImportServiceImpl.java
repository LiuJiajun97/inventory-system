package com.company.inventory.service.impl;

import com.company.inventory.common.constant.ImportExportLabels;
import com.company.inventory.common.exception.BizException;
import com.company.inventory.common.util.ExcelSupport;
import com.company.inventory.model.dto.customer.CustomerCreateDTO;
import com.company.inventory.model.dto.excel.CustomerImportRow;
import com.company.inventory.model.dto.excel.ItemImportRow;
import com.company.inventory.model.dto.excel.SupplierImportRow;
import com.company.inventory.model.dto.item.ItemCreateDTO;
import com.company.inventory.model.dto.supplier.SupplierCreateDTO;
import com.company.inventory.model.vo.excel.ImportResultVO;
import com.company.inventory.service.CustomerService;
import com.company.inventory.service.ImportService;
import com.company.inventory.service.ItemService;
import com.company.inventory.service.SupplierService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 导入服务实现:物品/供应商/客户。
 *
 * <p>逐行处理:行级校验(必填/数值/字典映射)与 Service.create 均可能失败,
 * 失败行记入汇总不阻断后续行;编码/条码重复由 Service.create 抛 BizException
 * 捕获后记入失败(含与库中已有重复 + 本批内重复,成功行先行落库)。</p>
 *
 * @author inventory
 */
@Service
public class ImportServiceImpl implements ImportService {

    /** 物品服务。 */
    private final ItemService itemService;

    /** 供应商服务。 */
    private final SupplierService supplierService;

    /** 客户服务。 */
    private final CustomerService customerService;

    /**
     * 构造服务。
     *
     * @param itemService     物品服务
     * @param supplierService 供应商服务
     * @param customerService 客户服务
     */
    public ImportServiceImpl(ItemService itemService, SupplierService supplierService,
            CustomerService customerService) {
        this.itemService = itemService;
        this.supplierService = supplierService;
        this.customerService = customerService;
    }

    /**
     * 物品导入。
     *
     * @param file xlsx 文件
     * @return 导入结果
     */
    @Override
    public ImportResultVO importItems(MultipartFile file) {
        List<ItemImportRow> rows = ExcelSupport.readXlsx(file, ItemImportRow.class);
        List<ImportResultVO.FailedRow> failed = new ArrayList<>();
        long imported = 0;
        for (int i = 0; i < rows.size(); i++) {
            int rowNo = ExcelSupport.FIRST_DATA_ROW_NO + i;
            ItemImportRow row = rows.get(i);
            if (isBlank(row.getItemCode(), row.getItemName(), row.getUnit(), row.getSpec(),
                    row.getCategory(), row.getBarcode(), row.getSecondUnit(), row.getConvertFactor(),
                    row.getBrand(), row.getOrigin(), row.getMinStock(), row.getDefaultTaxRate())) {
                continue;
            }
            String code = trimToEmpty(row.getItemCode());
            String reason = null;
            ItemCreateDTO dto = null;
            try {
                dto = toItemCreateDto(row);
            } catch (BizException e) {
                reason = e.getMessage();
            }
            if (reason == null) {
                try {
                    itemService.create(dto);
                    imported++;
                } catch (BizException e) {
                    reason = e.getMessage();
                }
            }
            if (reason != null) {
                failed.add(new ImportResultVO.FailedRow(rowNo, code, reason));
            }
        }
        return new ImportResultVO(imported, failed);
    }

    /**
     * 供应商导入。
     *
     * @param file     xlsx 文件
     * @param username 当前登录用户名
     * @return 导入结果
     */
    @Override
    public ImportResultVO importSuppliers(MultipartFile file, String username) {
        List<SupplierImportRow> rows = ExcelSupport.readXlsx(file, SupplierImportRow.class);
        List<ImportResultVO.FailedRow> failed = new ArrayList<>();
        long imported = 0;
        for (int i = 0; i < rows.size(); i++) {
            int rowNo = ExcelSupport.FIRST_DATA_ROW_NO + i;
            SupplierImportRow row = rows.get(i);
            if (isPartnerBlankRow(row)) {
                continue;
            }
            String code = trimToEmpty(row.getSupplierCode());
            String reason = null;
            SupplierCreateDTO dto = null;
            try {
                dto = toSupplierCreateDto(row);
            } catch (BizException e) {
                reason = e.getMessage();
            }
            if (reason == null) {
                try {
                    supplierService.create(dto, username);
                    imported++;
                } catch (BizException e) {
                    reason = e.getMessage();
                }
            }
            if (reason != null) {
                failed.add(new ImportResultVO.FailedRow(rowNo, code, reason));
            }
        }
        return new ImportResultVO(imported, failed);
    }

    /**
     * 客户导入。
     *
     * @param file     xlsx 文件
     * @param username 当前登录用户名
     * @return 导入结果
     */
    @Override
    public ImportResultVO importCustomers(MultipartFile file, String username) {
        List<CustomerImportRow> rows = ExcelSupport.readXlsx(file, CustomerImportRow.class);
        List<ImportResultVO.FailedRow> failed = new ArrayList<>();
        long imported = 0;
        for (int i = 0; i < rows.size(); i++) {
            int rowNo = ExcelSupport.FIRST_DATA_ROW_NO + i;
            CustomerImportRow row = rows.get(i);
            if (isPartnerBlankRow(row)) {
                continue;
            }
            String code = trimToEmpty(row.getCustomerCode());
            String reason = null;
            CustomerCreateDTO dto = null;
            try {
                dto = toCustomerCreateDto(row);
            } catch (BizException e) {
                reason = e.getMessage();
            }
            if (reason == null) {
                try {
                    customerService.create(dto, username);
                    imported++;
                } catch (BizException e) {
                    reason = e.getMessage();
                }
            }
            if (reason != null) {
                failed.add(new ImportResultVO.FailedRow(rowNo, code, reason));
            }
        }
        return new ImportResultVO(imported, failed);
    }

    /**
     * 物品导入模板下载。
     *
     * @param resp HTTP 响应
     */
    @Override
    public void writeItemTemplate(HttpServletResponse resp) {
        ItemImportRow example = new ItemImportRow();
        example.setItemCode("HW-SCREW-M8");
        example.setItemName("M8 内六角螺丝");
        example.setUnit("支");
        example.setSpec("M8x30");
        example.setCategory("五金");
        example.setBarcode("6901234567890");
        example.setSecondUnit("盒");
        example.setConvertFactor("100");
        example.setBrand("示例品牌");
        example.setOrigin("浙江温州");
        example.setMinStock("500");
        example.setDefaultTaxRate("13");
        ExcelSupport.writeXlsx(resp, "items-template", "物品导入模板", "物品导入",
                ItemImportRow.class, List.of(example));
    }

    /**
     * 供应商导入模板下载。
     *
     * @param resp HTTP 响应
     */
    @Override
    public void writeSupplierTemplate(HttpServletResponse resp) {
        SupplierImportRow example = new SupplierImportRow();
        example.setSupplierCode("SUP-001");
        example.setSupplierName("示例五金供应商");
        example.setContact("张三");
        example.setPhone("13800000000");
        example.setAddress("浙江省温州市龙湾区示例路 1 号");
        example.setSettleMethod("月结");
        example.setDefaultTaxRate("13");
        example.setTaxNo("91330300XXXXXXXXXX");
        example.setEmail("supplier@example.com");
        example.setBankName("工商银行龙湾支行");
        example.setBankAccount("6222020200112233445");
        example.setCreditLimit("500000");
        example.setPayTermDays("30");
        example.setDeliveryAddress("浙江省温州市龙湾区收货仓");
        ExcelSupport.writeXlsx(resp, "suppliers-template", "供应商导入模板", "供应商导入",
                SupplierImportRow.class, List.of(example));
    }

    /**
     * 客户导入模板下载。
     *
     * @param resp HTTP 响应
     */
    @Override
    public void writeCustomerTemplate(HttpServletResponse resp) {
        CustomerImportRow example = new CustomerImportRow();
        example.setCustomerCode("CUS-001");
        example.setCustomerName("示例制造客户");
        example.setContact("李四");
        example.setPhone("13900000000");
        example.setAddress("江苏省苏州市工业园区示例路 2 号");
        example.setSettleMethod("预付");
        example.setDefaultTaxRate("13");
        example.setTaxNo("91320500XXXXXXXXXX");
        example.setEmail("customer@example.com");
        example.setBankName("建设银行园区支行");
        example.setBankAccount("6217000010112233445");
        example.setCreditLimit("200000");
        example.setPayTermDays("0");
        example.setDeliveryAddress("江苏省苏州市工业园区客户仓");
        ExcelSupport.writeXlsx(resp, "customers-template", "客户导入模板", "客户导入",
                CustomerImportRow.class, List.of(example));
    }

    /**
     * 物品导入行转创建 DTO(行级校验:必填 + 数值 + 分类映射)。
     *
     * @param row Excel 行
     * @return 创建 DTO
     * @throws BizException 必填缺失 / 数值非法 / 分类无法识别
     */
    private ItemCreateDTO toItemCreateDto(ItemImportRow row) {
        String itemCode = require(row.getItemCode(), "物品编码必填");
        String itemName = require(row.getItemName(), "物品名称必填");
        String unit = require(row.getUnit(), "单位必填");
        String category = ImportExportLabels.categoryFromInput(row.getCategory());
        String barcode = trimToNull(row.getBarcode());
        String secondUnit = trimToNull(row.getSecondUnit());
        String brand = trimToNull(row.getBrand());
        String origin = trimToNull(row.getOrigin());
        return new ItemCreateDTO(
                itemCode, itemName, unit, trimToNull(row.getSpec()), null, category,
                parseDecimal(row.getMinStock(), "最低库存必须为数字"),
                parseDecimal(row.getDefaultTaxRate(), "默认税率必须为数字"),
                barcode, secondUnit,
                parseDecimal(row.getConvertFactor(), "换算率必须为数字"),
                brand, null, null, origin);
    }

    /**
     * 供应商导入行转创建 DTO(行级校验:必填 + 数值 + 结算方式映射)。
     *
     * @param row Excel 行
     * @return 创建 DTO
     * @throws BizException 必填缺失 / 数值非法 / 结算方式无法识别
     */
    private SupplierCreateDTO toSupplierCreateDto(SupplierImportRow row) {
        String code = require(row.getSupplierCode(), "供应商编码必填");
        String name = require(row.getSupplierName(), "供应商名称必填");
        String settleMethod = ImportExportLabels.settleFromInput(row.getSettleMethod());
        return new SupplierCreateDTO(
                code, name, trimToNull(row.getTaxNo()),
                parseDecimal(row.getDefaultTaxRate(), "默认税率必须为数字"),
                trimToNull(row.getContact()), trimToNull(row.getPhone()),
                trimToNull(row.getAddress()), settleMethod,
                parseInteger(row.getPayTermDays(), "付款条件必须为整数"),
                trimToNull(row.getBankName()), trimToNull(row.getBankAccount()),
                parseDecimal(row.getCreditLimit(), "信用额度必须为数字"),
                trimToNull(row.getDeliveryAddress()), trimToNull(row.getEmail()), null);
    }

    /**
     * 客户导入行转创建 DTO(行级校验:必填 + 数值 + 结算方式映射)。
     *
     * @param row Excel 行
     * @return 创建 DTO
     * @throws BizException 必填缺失 / 数值非法 / 结算方式无法识别
     */
    private CustomerCreateDTO toCustomerCreateDto(CustomerImportRow row) {
        String code = require(row.getCustomerCode(), "客户编码必填");
        String name = require(row.getCustomerName(), "客户名称必填");
        String settleMethod = ImportExportLabels.settleFromInput(row.getSettleMethod());
        return new CustomerCreateDTO(
                code, name, trimToNull(row.getTaxNo()),
                parseDecimal(row.getDefaultTaxRate(), "默认税率必须为数字"),
                trimToNull(row.getContact()), trimToNull(row.getPhone()),
                trimToNull(row.getAddress()), settleMethod,
                parseInteger(row.getPayTermDays(), "付款条件必须为整数"),
                trimToNull(row.getBankName()), trimToNull(row.getBankAccount()),
                parseDecimal(row.getCreditLimit(), "信用额度必须为数字"),
                trimToNull(row.getDeliveryAddress()), trimToNull(row.getEmail()), null);
    }

    /**
     * 供应商/客户导入行是否整行空白(全空白行跳过,不计入结果)。
     *
     * @param row 供应商行(按字段集合判断)
     * @return true 表示整行空白
     */
    private boolean isPartnerBlankRow(SupplierImportRow row) {
        return isBlank(row.getSupplierCode(), row.getSupplierName(), row.getContact(), row.getPhone(),
                row.getAddress(), row.getSettleMethod(), row.getDefaultTaxRate(), row.getTaxNo(),
                row.getEmail(), row.getBankName(), row.getBankAccount(), row.getCreditLimit(),
                row.getPayTermDays(), row.getDeliveryAddress());
    }

    /**
     * 客户导入行是否整行空白。
     *
     * @param row 客户行
     * @return true 表示整行空白
     */
    private boolean isPartnerBlankRow(CustomerImportRow row) {
        return isBlank(row.getCustomerCode(), row.getCustomerName(), row.getContact(), row.getPhone(),
                row.getAddress(), row.getSettleMethod(), row.getDefaultTaxRate(), row.getTaxNo(),
                row.getEmail(), row.getBankName(), row.getBankAccount(), row.getCreditLimit(),
                row.getPayTermDays(), row.getDeliveryAddress());
    }

    /**
     * 判定一组字段是否全部空白。
     *
     * @param values 字段值集合
     * @return true 表示全部空白
     */
    private boolean isBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 取必填字段(trim 后空则抛业务异常)。
     *
     * @param value    字段值
     * @param errMsg   缺失时的错误消息
     * @return trim 后的值
     * @throws BizException 字段为空
     */
    private String require(String value, String errMsg) {
        String v = trimToNull(value);
        if (v == null) {
            throw new BizException(errMsg);
        }
        return v;
    }

    /**
     * 可选数值字段转 BigDecimal(空返回 null,非法抛业务异常)。
     *
     * @param value  字段值
     * @param errMsg 非法时的错误消息
     * @return BigDecimal 或 null
     * @throws BizException 数值非法
     */
    private BigDecimal parseDecimal(String value, String errMsg) {
        String v = trimToNull(value);
        if (v == null) {
            return null;
        }
        try {
            return new BigDecimal(v);
        } catch (NumberFormatException e) {
            throw new BizException(errMsg);
        }
    }

    /**
     * 可选整数字段转 Integer(空返回 null,非法抛业务异常)。
     *
     * @param value  字段值
     * @param errMsg 非法时的错误消息
     * @return Integer 或 null
     * @throws BizException 数值非法
     */
    private Integer parseInteger(String value, String errMsg) {
        String v = trimToNull(value);
        if (v == null) {
            return null;
        }
        try {
            return Integer.valueOf(v);
        } catch (NumberFormatException e) {
            throw new BizException(errMsg);
        }
    }

    /**
     * trim 后空白转 null。
     *
     * @param value 原始值(可空)
     * @return trim 后的值或 null
     */
    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    /**
     * trim 后空白转空串(失败行 code 列用)。
     *
     * @param value 原始值(可空)
     * @return trim 后的值或空串
     */
    private String trimToEmpty(String value) {
        String v = trimToNull(value);
        return v == null ? "" : v;
    }
}
