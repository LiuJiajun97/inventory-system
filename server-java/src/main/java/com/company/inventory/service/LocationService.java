package com.company.inventory.service;

import com.company.inventory.common.page.PageResult;
import com.company.inventory.dto.location.LocationCreateDTO;
import com.company.inventory.query.LocationQuery;
import com.company.inventory.vo.location.LocationVO;














/**
 * 库位服务接口。
 *
 * @author inventory
 */
public interface LocationService {

    /**
     * 库位分页列表(按 id 升序,可按仓库过滤)。
     *
     * @param query 查询条件(warehouseId/page/pageSize)
     * @return 分页结果
     */
    PageResult<LocationVO> list(LocationQuery query);

    /**
     * 新建库位(仅 admin),仓库内编码重复抛 400。
     *
     * @param dto 入参
     * @return 新建库位
     */
    LocationVO create(LocationCreateDTO dto);
}
