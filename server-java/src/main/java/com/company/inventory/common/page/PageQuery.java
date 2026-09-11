package com.company.inventory.common.page;

import com.company.inventory.common.constant.ErrorCode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

/**
 * 分页查询基类:所有列表接口的查询条件对象(*Query)统一继承本类。
 *
 * <p>GET 查询参数自动绑定到 page/pageSize 两个字段(参数名与前端契约一致);
 * page 默认 1,pageSize 默认 {@link #DEFAULT_PAGE_SIZE},
 * 最小 1,上限 {@link ErrorCode#PAGE_SIZE_MAX}。
 * 用 getter/setter 类(非 record),保证 Spring MVC 的 POJO 绑定可用。</p>
 *
 * @author inventory
 */
@Getter
@Setter
public class PageQuery {

    /** 默认页码。 */
    public static final long DEFAULT_PAGE = 1L;

    /** 默认每页条数。 */
    public static final long DEFAULT_PAGE_SIZE = 20L;

    /** 页码(从 1 起)。 */
    @Min(1)
    private long page = DEFAULT_PAGE;

    /** 每页条数(默认 20,上限 {@link ErrorCode#PAGE_SIZE_MAX})。 */
    @Min(1)
    @Max(ErrorCode.PAGE_SIZE_MAX)
    private long pageSize = DEFAULT_PAGE_SIZE;
}
