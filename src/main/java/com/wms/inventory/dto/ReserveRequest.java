package com.wms.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ReserveRequest {

    @NotBlank(message = "货品编码不能为空")
    private String skuCode;

    @NotNull(message = "预占数量不能为空")
    @Min(value = 1, message = "预占数量必须大于0")
    private Integer qty;

    private String orderNo;

    private LocalDateTime expireAt;

    private Integer timeoutMinutes;

    private String operator;
    private String remark;
}
