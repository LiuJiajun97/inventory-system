package com.company.inventory.controller;

import com.company.inventory.common.page.PageResult;
import com.company.inventory.config.RequirePerm;
import com.company.inventory.model.dto.location.LocationCreateDTO;
import com.company.inventory.model.dto.location.LocationUpdateDTO;
import com.company.inventory.model.dto.warehouse.WarehouseCreateDTO;
import com.company.inventory.model.dto.warehouse.WarehouseUpdateDTO;
import com.company.inventory.model.query.LocationQuery;
import com.company.inventory.model.query.WarehouseQuery;
import com.company.inventory.service.LocationService;
import com.company.inventory.service.WarehouseService;
import com.company.inventory.model.vo.location.LocationVO;
import com.company.inventory.model.vo.warehouse.WarehouseVO;

















import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 仓库与库位接口。
 *
 * @author inventory
 */
@Tag(name = "仓库")
@RestController
@RequestMapping("/api/v1")
@Validated
public class WarehouseController {

    /** 仓库服务。 */
    private final WarehouseService warehouseService;
    /** 库位服务。 */
    private final LocationService locationService;

    /**
     * 构造控制器。
     *
     * @param warehouseService 仓库服务
     * @param locationService  库位服务
     */
    public WarehouseController(WarehouseService warehouseService, LocationService locationService) {
        this.warehouseService = warehouseService;
        this.locationService = locationService;
    }

    /**
     * 仓库列表(分页)。
     *
     * @param query 查询条件(page/pageSize)
     * @return 分页结果
     */
    @Operation(summary = "仓库列表")
    @GetMapping("/warehouses")
    public PageResult<WarehouseVO> listWarehouses(@Valid WarehouseQuery query) {
        return warehouseService.list(query);
    }

    /**
     * 仓库详情。
     *
     * @param id 仓库 ID
     * @return 仓库
     */
    @Operation(summary = "仓库详情")
    @GetMapping("/warehouses/{id}")
    public WarehouseVO getWarehouse(@PathVariable long id) {
        return warehouseService.get(id);
    }

    /**
     * 新建仓库(仅 admin)。
     *
     * @param dto 入参
     * @return 新建仓库
     */
    @Operation(summary = "新建仓库")
    @PostMapping("/warehouses")
    @RequirePerm("warehouse:create")
    public WarehouseVO createWarehouse(@Valid @RequestBody WarehouseCreateDTO dto) {
        return warehouseService.create(dto);
    }

    /**
     * 编辑仓库(仅 admin,编码不可改)。
     *
     * <p>含防数据不一致校验:enableLocation 1→0 / enableSerial 1→0 / 停用有库存时
     * 均返回 400,具体规则见 service 层。</p>
     *
     * @param id  仓库 ID
     * @param dto 入参(至少一个字段非空)
     * @return 更新后的仓库
     */
    @Operation(summary = "编辑仓库")
    @PutMapping("/warehouses/{id}")
    @RequirePerm("warehouse:edit")
    public WarehouseVO updateWarehouse(@PathVariable long id,
            @Valid @RequestBody WarehouseUpdateDTO dto) {
        return warehouseService.update(id, dto);
    }

    /**
     * 库位列表(分页)。
     *
     * @param query 查询条件(warehouseId/page/pageSize)
     * @return 分页结果
     */
    @Operation(summary = "库位列表")
    @GetMapping("/locations")
    public PageResult<LocationVO> listLocations(@Valid LocationQuery query) {
        return locationService.list(query);
    }

    /**
     * 新建库位(仅 admin)。
     *
     * @param dto 入参
     * @return 新建库位
     */
    @Operation(summary = "新建库位")
    @PostMapping("/locations")
    @RequirePerm("location:create")
    public LocationVO createLocation(@Valid @RequestBody LocationCreateDTO dto) {
        return locationService.create(dto);
    }

    /**
     * 编辑库位(仅 admin,编码与所属仓库不可改,LocationDO 无 status 字段不开放停用)。
     *
     * @param id  库位 ID
     * @param dto 入参
     * @return 更新后的库位
     */
    @Operation(summary = "编辑库位")
    @PutMapping("/locations/{id}")
    @RequirePerm("location:edit")
    public LocationVO updateLocation(@PathVariable long id,
            @Valid @RequestBody LocationUpdateDTO dto) {
        return locationService.update(id, dto);
    }
}
