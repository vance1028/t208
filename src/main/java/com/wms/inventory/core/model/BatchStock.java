package com.wms.inventory.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchStock {
    private Long id;
    private String skuCode;
    private String batchNo;
    private String locationCode;
    private LocalDate productionDate;
    private LocalDate expiryDate;
    private int physicalQty;
    private int reservedQty;
    private int availableQty;
    private int version;
}
