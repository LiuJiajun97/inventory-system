package com.company.inventory.common.page;

import com.company.inventory.common.constant.ErrorCode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 分页查询基类:所有列表接口的查询条件对象(*Query)统一继承本类。
 *
 * <p>GET 查询参数自动绑定到 page/pageSize 两个字段(参数名与前端契约一致);
 * page 默认 1,pageSize 默认 {@link #DEFAULT_PAGE_SIZE},
 * 最小 1,上限 {@link ErrorCode#PAGE_SIZE_MAX}。
 * 使用 getter/setter 而非 record,保证 Spring MVC 的 POJO 绑定可用。</p>
 *
 * @author inventory
 */
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

    /**
     * 获取页码。
     *
     * @return 页码(从 1 起)
     */
    public long getPage() {
        return page;
    }

    /**
     * 设置页码。
     *
     * @param page 页码(从 1 起)
     */
    public void setPage(long page) {
        this.page = page;
    }

    /**
     * 获取每页条数。
     *
     * @return 每页条数
     */
    public long getPageSize() {
        return pageSize;
    }

    /**
     * 设置每页条数。
     *
     * @param pageSize 每页条数
     */
    public void setPageSize(long pageSize) {
        this.pageSize = pageSize;
    }
}
