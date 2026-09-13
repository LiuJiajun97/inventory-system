package com.company.inventory.controller;

import com.company.inventory.common.page.PageResult;
import com.company.inventory.config.RequireRole;
import com.company.inventory.model.dto.item.ItemCreateDTO;
import com.company.inventory.model.dto.item.ItemUpdateDTO;
import com.company.inventory.model.query.ItemQuery;
import com.company.inventory.model.vo.excel.ImportResultVO;
import com.company.inventory.service.ExportService;
import com.company.inventory.service.ImportService;
import com.company.inventory.service.ItemService;
import com.company.inventory.model.vo.item.ItemVO;











import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 物品接口。
 *
 * @author inventory
 */
@Tag(name = "物品")
@RestController
@RequestMapping("/api/v1/items")
public class ItemController {

    /** 物品服务。 */
    private final ItemService itemService;

    /** 导入服务。 */
    private final ImportService importService;

    /** 导出服务。 */
    private final ExportService exportService;

    /**
     * 构造控制器。
     *
     * @param itemService   物品服务
     * @param importService 导入服务
     * @param exportService 导出服务
     */
    public ItemController(ItemService itemService, ImportService importService,
            ExportService exportService) {
        this.itemService = itemService;
        this.importService = importService;
        this.exportService = exportService;
    }

    /**
     * 物品列表(分页)。
     *
     * @param query 查询条件(keyword/page/pageSize)
     * @return 分页结果
     */
    @Operation(summary = "物品列表")
    @GetMapping
    public PageResult<ItemVO> list(@Valid ItemQuery query) {
        return itemService.list(query);
    }

    /**
     * 物品详情。
     *
     * @param id 物品 ID
     * @return 物品
     */
    @Operation(summary = "物品详情")
    @GetMapping("/{id}")
    public ItemVO get(@PathVariable long id) {
        return itemService.get(id);
    }

    /**
     * 新建物品(仅 admin)。
     *
     * @param dto 入参
     * @return 新建物品
     */
    @Operation(summary = "新建物品")
    @PostMapping
    @RequireRole("admin")
    public ItemVO create(@Valid @RequestBody ItemCreateDTO dto) {
        return itemService.create(dto);
    }

    /**
     * 编辑物品(仅 admin,编码不可改)。
     *
     * @param id  物品 ID
     * @param dto 入参
     * @return 更新后的物品
     */
    @Operation(summary = "编辑物品")
    @PutMapping("/{id}")
    @RequireRole("admin")
    public ItemVO update(@PathVariable long id, @Valid @RequestBody ItemUpdateDTO dto) {
        return itemService.update(id, dto);
    }

    /**
     * 物品导入(仅 admin,逐行校验,成功行走既有创建链路)。
     *
     * @param file xlsx 文件(列头须与模板一致)
     * @return 导入结果(imported + 失败行汇总)
     */
    @Operation(summary = "物品导入")
    @PostMapping("/import")
    @RequireRole("admin")
    public ImportResultVO importItems(@RequestParam("file") MultipartFile file) {
        return importService.importItems(file);
    }

    /**
     * 物品导出(xlsx,不分页,权限与列表读一致)。
     *
     * @param query 列表查询条件(keyword/itemCategory,分页参数忽略)
     * @param resp  HTTP 响应(xlsx 流)
     */
    @Operation(summary = "物品导出")
    @GetMapping("/export")
    public void exportItems(@Valid ItemQuery query, HttpServletResponse resp) {
        exportService.exportItems(query, resp);
    }

    /**
     * 物品导入模板下载(表头 + 1 行示例)。
     *
     * @param resp HTTP 响应(xlsx 流)
     */
    @Operation(summary = "物品导入模板")
    @GetMapping("/template")
    public void itemTemplate(HttpServletResponse resp) {
        importService.writeItemTemplate(resp);
    }
}
