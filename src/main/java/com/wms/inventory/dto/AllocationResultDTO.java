package com.wms.inventory.dto;

import com.wms.inventory.core.model.AllocationDetail;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AllocationResultDTO {

    private boolean success;
    private int requestedQty;
    private int totalAvailable;
    private int shortageQty;
    private String message;

    @Builder.Default
    private List<AllocationDetail> details = new ArrayList<>();
}
