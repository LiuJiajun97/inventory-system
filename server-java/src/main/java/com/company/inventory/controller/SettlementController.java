package com.company.inventory.controller;

import com.company.inventory.model.vo.settlement.LedgerRowVO;
import com.company.inventory.model.vo.settlement.SettlementDashboardVO;
import com.company.inventory.service.SettlementService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 结算台账接口(V18 结算域):应付/应收台账 + 仪表盘余额,全部只读(零建表实时聚合)。
 *
 * @author inventory
 */
@Tag(name = "结算台账")
@RestController
@RequestMapping("/api/v1/settlement")
@Validated
public class SettlementController {

    /** 结算台账服务。 */
    private final SettlementService settlementService;

    /**
     * 构造控制器。
     *
     * @param settlementService 结算台账服务
     */
    public SettlementController(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    /**
     * 应付台账(按供应商:入库额/已开票/待开票暂估/已付/应付余额 + 发票展开 + 订单执行)。
     *
     * @return 台账行列表
     */
    @Operation(summary = "应付台账")
    @GetMapping("/ap")
    public List<LedgerRowVO> apLedger() {
        return settlementService.apLedger();
    }

    /**
     * 应收台账(按客户,对称应付)。
     *
     * @return 台账行列表
     */
    @Operation(summary = "应收台账")
    @GetMapping("/ar")
    public List<LedgerRowVO> arLedger() {
        return settlementService.arLedger();
    }

    /**
     * 仪表盘余额(应付余额/应收余额两卡)。
     *
     * @return 余额
     */
    @Operation(summary = "仪表盘余额")
    @GetMapping("/dashboard")
    public SettlementDashboardVO dashboard() {
        return settlementService.dashboard();
    }
}
