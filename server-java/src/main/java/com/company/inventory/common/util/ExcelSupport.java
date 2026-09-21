package com.company.inventory.common.util;

import com.alibaba.excel.EasyExcel;
import com.company.inventory.common.exception.BizException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Excel 读写支撑:统一 xlsx 响应头、EasyExcel 写出/同步读、导入文件校验。
 *
 * <p>响应头契约:Content-Type 为 xlsx MIME,Content-Disposition 携带
 * 英文 filename(兜底)与 URLEncode 的 filename*(中文文件名)。</p>
 *
 * @author inventory
 */
public final class ExcelSupport {

    /** xlsx MIME 类型。 */
    public static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /** 导出/模板单页最大行数(单机轻量部署数据量远小于此,不分页一次取完)。 */
    public static final long EXPORT_PAGE_SIZE = 100000L;

    /** 数据行起始的 Excel 行号(第 1 行为表头)。 */
    public static final int FIRST_DATA_ROW_NO = 2;

    /** xlsx 文件头 ZIP 魔数第 1 字节(PK 的 P)。 */
    private static final int ZIP_MAGIC_1 = 0x50;

    /** xlsx 文件头 ZIP 魔数第 2 字节(PK 的 K)。 */
    private static final int ZIP_MAGIC_2 = 0x4B;

    private ExcelSupport() {
    }

    /**
     * 设置 xlsx 下载响应头(中文文件名 URLEncode 后放入 filename*)。
     *
     * @param resp       HTTP 响应
     * @param filenameEn 英文文件名(不含扩展名,兜底)
     * @param filenameCn 中文文件名(不含扩展名)
     */
    public static void prepareXlsxResponse(HttpServletResponse resp, String filenameEn, String filenameCn) {
        String encoded = URLEncoder.encode(filenameCn, StandardCharsets.UTF_8).replace("+", "%20");
        resp.setContentType(XLSX_CONTENT_TYPE);
        resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resp.setHeader("Content-Disposition",
                "attachment; filename=\"" + filenameEn + ".xlsx\"; filename*=UTF-8''" + encoded + ".xlsx");
    }

    /**
     * EasyExcel 同步写出(设置响应头后写表头 + 数据行,空数据也写表头)。
     *
     * @param <T>        行模型类型
     * @param resp       HTTP 响应
     * @param filenameEn 英文文件名(不含扩展名)
     * @param filenameCn 中文文件名(不含扩展名)
     * @param sheetName  Sheet 名
     * @param headClass  行模型类(@ExcelProperty 列头)
     * @param rows       数据行(可为空)
     */
    public static <T> void writeXlsx(HttpServletResponse resp, String filenameEn, String filenameCn,
            String sheetName, Class<T> headClass, List<T> rows) {
        prepareXlsxResponse(resp, filenameEn, filenameCn);
        try (OutputStream out = resp.getOutputStream()) {
            EasyExcel.write(out, headClass).sheet(sheetName).doWrite(rows == null ? List.of() : rows);
            out.flush();
        } catch (IOException e) {
            throw new BizException("Excel 写出失败: " + e.getMessage());
        }
    }

    /**
     * EasyExcel 同步读 xlsx(仅支持 .xlsx、非空,且文件头必须是 ZIP 魔数 PK,
     * 防止非 xlsx 字节被当空表静默放过;解析失败抛业务异常)。
     *
     * @param file      上传文件
     * @param headClass 行模型类
     * @param <T>       行模型类型
     * @return 数据行列表(第 1 行为表头)
     */
    public static <T> List<T> readXlsx(MultipartFile file, Class<T> headClass) {
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase(java.util.Locale.ROOT).endsWith(".xlsx")) {
            throw new BizException("仅支持 .xlsx 文件");
        }
        if (file.isEmpty()) {
            throw new BizException("文件为空,请选择有效的 Excel 文件");
        }
        try (InputStream in = file.getInputStream()) {
            byte[] buf = in.readAllBytes();
            if (buf.length < 2 || buf[0] != ZIP_MAGIC_1 || buf[1] != ZIP_MAGIC_2) {
                throw new BizException("文件不是有效的 xlsx(文件头非法)");
            }
            List<T> rows = EasyExcel.read(new java.io.ByteArrayInputStream(buf))
                    .head(headClass).sheet().doReadSync();
            return rows == null ? List.of() : rows;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("Excel 解析失败: " + e.getMessage());
        }
    }
}
