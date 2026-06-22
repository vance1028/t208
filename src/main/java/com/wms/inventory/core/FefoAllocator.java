package com.wms.inventory.core;

import com.wms.inventory.core.model.AllocationDetail;
import com.wms.inventory.core.model.BatchStock;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class FefoAllocator {

    private FefoAllocator() {
    }

    public static class AllocationResult {
        private final boolean success;
        private final int requestedQty;
        private final int totalAvailable;
        private final int shortageQty;
        private final List<AllocationDetail> details;

        private AllocationResult(boolean success, int requestedQty, int totalAvailable,
                                 int shortageQty, List<AllocationDetail> details) {
            this.success = success;
            this.requestedQty = requestedQty;
            this.totalAvailable = totalAvailable;
            this.shortageQty = shortageQty;
            this.details = details;
        }

        public boolean isSuccess() { return success; }
        public int getRequestedQty() { return requestedQty; }
        public int getTotalAvailable() { return totalAvailable; }
        public int getShortageQty() { return shortageQty; }
        public List<AllocationDetail> getDetails() { return details; }

        public static AllocationResult success(int requestedQty, List<AllocationDetail> details) {
            return new AllocationResult(true, requestedQty, requestedQty, 0, details);
        }

        public static AllocationResult fail(int requestedQty, int totalAvailable, int shortageQty,
                                            List<AllocationDetail> details) {
            return new AllocationResult(false, requestedQty, totalAvailable, shortageQty, details);
        }
    }

    public static AllocationResult allocate(String skuCode, int requestedQty, List<BatchStock> batches) {
        if (requestedQty <= 0) {
            throw new IllegalArgumentException("请求数量必须大于0");
        }

        List<BatchStock> sortedBatches = new ArrayList<>(batches);
        sortedBatches.sort(Comparator
                .comparing(BatchStock::getExpiryDate)
                .thenComparing(BatchStock::getProductionDate)
                .thenComparing(BatchStock::getId, Comparator.nullsLast(Comparator.naturalOrder())));

        int totalAvailable = sortedBatches.stream()
                .mapToInt(BatchStock::getAvailableQty)
                .sum();

        if (totalAvailable < requestedQty) {
            List<AllocationDetail> partial = buildPartialAllocations(sortedBatches);
            return AllocationResult.fail(requestedQty, totalAvailable, requestedQty - totalAvailable, partial);
        }

        List<AllocationDetail> allocations = new ArrayList<>();
        int remaining = requestedQty;

        for (BatchStock batch : sortedBatches) {
            if (remaining <= 0) break;
            if (batch.getAvailableQty() <= 0) continue;

            int takeQty = Math.min(batch.getAvailableQty(), remaining);
            allocations.add(AllocationDetail.builder()
                    .skuCode(skuCode)
                    .batchNo(batch.getBatchNo())
                    .locationCode(batch.getLocationCode())
                    .productionDate(batch.getProductionDate())
                    .expiryDate(batch.getExpiryDate())
                    .allocatedQty(takeQty)
                    .build());
            remaining -= takeQty;
        }

        if (remaining > 0) {
            return AllocationResult.fail(requestedQty, requestedQty - remaining, remaining, allocations);
        }

        return AllocationResult.success(requestedQty, allocations);
    }

    private static List<AllocationDetail> buildPartialAllocations(List<BatchStock> sortedBatches) {
        List<AllocationDetail> partial = new ArrayList<>();
        for (BatchStock batch : sortedBatches) {
            if (batch.getAvailableQty() > 0) {
                partial.add(AllocationDetail.builder()
                        .skuCode(batch.getSkuCode())
                        .batchNo(batch.getBatchNo())
                        .locationCode(batch.getLocationCode())
                        .productionDate(batch.getProductionDate())
                        .expiryDate(batch.getExpiryDate())
                        .allocatedQty(batch.getAvailableQty())
                        .build());
            }
        }
        return partial;
    }
}
