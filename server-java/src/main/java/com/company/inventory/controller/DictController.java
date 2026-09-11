package com.company.inventory.controller;

import com.company.inventory.config.RequireRole;
import com.company.inventory.dto.dict.DictCreateDTO;
import com.company.inventory.dto.dict.DictStatusDTO;
import com.company.inventory.dto.dict.DictTypeCreateDTO;
import com.company.inventory.dto.dict.DictTypeUpdateDTO;
import com.company.inventory.dto.dict.DictUpdateDTO;
import com.company.inventory.service.DictAdminService;
import com.company.inventory.service.DictService;
import com.company.inventory.service.DictTypeAdminService;
import com.company.inventory.vo.dict.DictOptionVO;
import com.company.inventory.vo.dict.DictTypeVO;
import com.company.inventory.vo.dict.DictVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 字典接口(所有登录角色可读;admin 可建可改可停用)。
 *
 * @author inventory
 */
@Tag(name = "字典")
@RestController
@RequestMapping("/api/v1/dicts")
public class DictController {

    /** 字典只读服务。 */
    private final DictService dictService;

    /** 字典管理服务。 */
    private final DictAdminService dictAdminService;

    /** 字典类型管理服务。 */
    private final DictTypeAdminService dictTypeAdminService;

    /**
     * 构造控制器。
     *
     * @param dictService         字典只读服务
     * @param dictAdminService    字典管理服务
     * @param dictTypeAdminService 字典类型管理服务
     */
    public DictController(DictService dictService,
                          DictAdminService dictAdminService,
                          DictTypeAdminService dictTypeAdminService) {
        this.dictService = dictService;
        this.dictAdminService = dictAdminService;
        this.dictTypeAdminService = dictTypeAdminService;
    }

    /**
     * 查询全部字典类型(含停用,带启用项数,前端左侧树用)。
     *
     * @return 类型列表
     */
    @Operation(summary = "查询全部字典类型")
    @GetMapping("/types")
    public List<DictTypeVO> listTypes() {
        return dictTypeAdminService.listAll();
    }

    /**
     * 新建字典类型(仅 admin,typeCode 重复时 400)。
     *
     * @param dto 入参
     * @return 新建类型
     */
    @Operation(summary = "新建字典类型")
    @PostMapping("/types")
    @RequireRole("admin")
    public DictTypeVO createType(@Valid @RequestBody DictTypeCreateDTO dto) {
        return dictTypeAdminService.create(dto);
    }

    /**
     * 编辑字典类型(仅 admin,改 typeName/remark/status,typeCode 不可改)。
     *
     * @param typeCode 类型编码
     * @param dto      入参
     * @return 更新后类型
     */
    @Operation(summary = "编辑字典类型")
    @PutMapping("/types/{typeCode}")
    @RequireRole("admin")
    public DictTypeVO updateType(@PathVariable String typeCode,
                                 @Valid @RequestBody DictTypeUpdateDTO dto) {
        return dictTypeAdminService.update(typeCode, dto);
    }

    /**
     * 按类型查字典项(只返回启用项,按 sortOrder 升序)。
     *
     * @param type 字典类型(如 warehouseType)
     * @return 字典项列表({code, label})
     */
    @Operation(summary = "按类型查字典")
    @GetMapping
    public List<DictOptionVO> list(@RequestParam("type") String type) {
        return dictService.listByType(type);
    }

    /**
     * 查询指定类型的全部字典项(含停用项,管理界面用)。
     *
     * @param type 字典类型
     * @return 字典项列表(含 id/全部字段)
     */
    @Operation(summary = "按类型查全部字典项(含停用)")
    @GetMapping("/all")
    @RequireRole("admin")
    public List<DictVO> listAll(@RequestParam("type") String type) {
        return dictAdminService.listAllByType(type);
    }

    /**
     * 新建字典项(仅 admin,dictType+dictKey 冲突时 400)。
     *
     * @param dto 入参
     * @return 新建字典项
     */
    @Operation(summary = "新建字典项")
    @PostMapping
    @RequireRole("admin")
    public DictVO create(@Valid @RequestBody DictCreateDTO dto) {
        return dictAdminService.create(dto);
    }

    /**
     * 编辑字典项(仅 admin,仅 dictLabel/sortOrder 可改,dictType/dictKey 不可改)。
     *
     * @param id  字典项 ID
     * @param dto 入参
     * @return 更新后字典项
     */
    @Operation(summary = "编辑字典项")
    @PutMapping("/{id}")
    @RequireRole("admin")
    public DictVO update(@PathVariable long id, @Valid @RequestBody DictUpdateDTO dto) {
        return dictAdminService.update(id, dto);
    }

    /**
     * 启用/停用字典项(仅 admin,停用前校验是否被引用)。
     *
     * @param id  字典项 ID
     * @param dto 入参{status:1|0}
     */
    @Operation(summary = "启用/停用字典项")
    @PostMapping("/{id}/status")
    @RequireRole("admin")
    public void updateStatus(@PathVariable long id, @Valid @RequestBody DictStatusDTO dto) {
        dictAdminService.updateStatus(id, dto.status());
    }
}
