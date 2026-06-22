package com.wms.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.time.LocalDate;

@Data
public class SkuCreateRequest {

    @NotBlank(message = "货品编码不能为空")
    private String skuCode;

    @NotBlank(message = "货品名称不能为空")
    private String skuName;

    private String unit = "件";

    private Integer shelfLifeDays;

    private String description;
}
