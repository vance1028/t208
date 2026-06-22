package com.wms.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TransferRequest {

    @NotBlank(message = "货品编码不能为空")
    private String skuCode;

    @NotBlank(message = "批次号不能为空")
    private String batchNo;

    @NotBlank(message = "源库位不能为空")
    private String fromLocation;

    @NotBlank(message = "目标库位不能为空")
    private String toLocation;

    @NotNull(message = "调拨数量不能为空")
    @Min(value = 1, message = "调拨数量必须大于0")
    private Integer qty;

    private String operator;
    private String remark;
}
