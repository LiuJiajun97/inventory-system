package com.company.inventory.service.impl;

import com.company.inventory.common.constant.ErrorCode;
import com.company.inventory.common.exception.BizException;
import com.company.inventory.common.page.PageResult;
import com.company.inventory.dto.location.LocationCreateDTO;
import com.company.inventory.dto.location.LocationUpdateDTO;
import com.company.inventory.entity.location.LocationDO;
import com.company.inventory.mapper.LocationMapper;
import com.company.inventory.query.LocationQuery;
import com.company.inventory.service.LocationService;
import com.company.inventory.vo.location.LocationVO;















import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;







import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 库位服务实现。
 *
 * @author inventory
 */
@Service
public class LocationServiceImpl implements LocationService {

    /** 日志。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(LocationServiceImpl.class);

    /** 库位 Mapper。 */
    private final LocationMapper locationMapper;

    /**
     * 构造服务。
     *
     * @param locationMapper 库位 Mapper
     */
    public LocationServiceImpl(LocationMapper locationMapper) {
        this.locationMapper = locationMapper;
    }

    /**
     * 库位分页列表(按 id 升序,可按仓库过滤)。
     *
     * @param query 查询条件(warehouseId/page/pageSize)
     * @return 分页结果
     */
    @Override
    public PageResult<LocationVO> list(LocationQuery query) {
        LambdaQueryWrapper<LocationDO> wrapper = new LambdaQueryWrapper<>();
        if (query.getWarehouseId() != null) {
            wrapper.eq(LocationDO::getWarehouseId, query.getWarehouseId());
        }
        wrapper.orderByAsc(LocationDO::getId);
        Page<LocationDO> page = locationMapper.selectPage(
                Page.of(query.getPage(), query.getPageSize()), wrapper);
        List<LocationVO> vos = page.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(vos, page.getTotal(), query.getPage(), query.getPageSize());
    }

    /**
     * 新建库位。
     *
     * @param dto 入参
     * @return 新建库位
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public LocationVO create(LocationCreateDTO dto) {
        Long exist = locationMapper.selectCount(new LambdaQueryWrapper<LocationDO>()
                .eq(LocationDO::getWarehouseId, dto.warehouseId())
                .eq(LocationDO::getLocationCode, dto.locationCode()));
        if (exist != null && exist > 0) {
            throw new BizException("库位编码在该仓库内已存在",
                    ErrorCode.LOCATION_CODE_DUP, ErrorCode.HTTP_BAD_REQUEST);
        }
        LocationDO location = new LocationDO();
        location.setWarehouseId(dto.warehouseId());
        location.setLocationCode(dto.locationCode());
        location.setLocationName(dto.locationName());
        locationMapper.insert(location);
        LOGGER.info("新建库位: warehouseId={}, code={}", dto.warehouseId(), dto.locationCode());
        return toVO(location);
    }

    /**
     * 实体转 VO。
     *
     * @param location 实体
     * @return VO
     */
    private LocationVO toVO(LocationDO location) {
        return new LocationVO(location.getId(), location.getWarehouseId(),
                location.getLocationCode(), location.getLocationName());
    }

    /**
     * 编辑库位(仅 admin,编码与所属仓库不可改,LocationDO 无 status 字段不开放停用)。
     *
     * @param id  库位 ID
     * @param dto 入参
     * @return 更新后的库位
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public LocationVO update(long id, LocationUpdateDTO dto) {
        LocationDO location = locationMapper.selectById(id);
        if (location == null) {
            throw BizException.notFound("库位不存在");
        }
        if (dto.locationName() != null) {
            location.setLocationName(dto.locationName());
        }
        locationMapper.updateById(location);
        LOGGER.info("编辑库位: id={}, code={}", id, location.getLocationCode());
        return toVO(location);
    }
}
