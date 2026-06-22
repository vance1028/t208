package com.wms.inventory.dto;

import com.wms.inventory.core.ExpiryChecker;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpiryAlertDTO {

    private String skuCode;
    private String skuName;
    private String batchNo;
    private String locationCode;
    private LocalDate productionDate;
    private LocalDate expiryDate;
    private int physicalQty;
    private int reservedQty;
    private int availableQty;
    private long daysUntilExpiry;
    private String expiryStatus;
}
