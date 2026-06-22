package com.wms.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class InboundRequest {

    @NotBlank(message = "货品编码不能为空")
    private String skuCode;

    @NotBlank(message = "批次号不能为空")
    private String batchNo;

    @NotBlank(message = "库位编码不能为空")
    private String locationCode;

    @NotNull(message = "生产日期不能为空")
    private LocalDate productionDate;

    @NotNull(message = "到期日期不能为空")
    private LocalDate expiryDate;

    @NotNull(message = "入库数量不能为空")
    @Min(value = 1, message = "入库数量必须大于0")
    private Integer qty;

    private String operator;
    private String remark;
}
