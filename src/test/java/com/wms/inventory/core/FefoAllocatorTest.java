package com.wms.inventory.core;

import com.wms.inventory.core.model.AllocationDetail;
import com.wms.inventory.core.model.BatchStock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FefoAllocatorTest {

    private static final String SKU = "TEST-SKU";

    private BatchStock batch(String batchNo, LocalDate expiry, int availableQty) {
        return BatchStock.builder()
                .id((long) batchNo.hashCode())
                .skuCode(SKU)
                .batchNo(batchNo)
                .locationCode("LOC-" + batchNo)
                .productionDate(expiry.minusDays(100))
                .expiryDate(expiry)
                .physicalQty(availableQty + 10)
                .reservedQty(10)
                .availableQty(availableQty)
                .build();
    }

    @Test
    @DisplayName("FEFO: 单批次足够时只扣这一批")
    void singleBatchEnough() {
        LocalDate today = LocalDate.now();
        List<BatchStock> batches = List.of(
                batch("B1", today.plusDays(30), 50),
                batch("B2", today.plusDays(60), 100),
                batch("B3", today.plusDays(90), 100));

        FefoAllocator.AllocationResult result = FefoAllocator.allocate(SKU, 30, batches);

        assertTrue(result.isSuccess());
        assertEquals(1, result.getDetails().size());
        AllocationDetail d = result.getDetails().get(0);
        assertEquals("B1", d.getBatchNo());
        assertEquals(30, d.getAllocatedQty());
    }

    @Test
    @DisplayName("FEFO: 必须先扣最早到期的批次，不能跳过")
    void mustPickEarliestExpiryFirst() {
        LocalDate today = LocalDate.now();
        BatchStock early = batch("B-EARLY", today.plusDays(10), 5);
        BatchStock middle = batch("B-MID", today.plusDays(30), 50);
        BatchStock late = batch("B-LATE", today.plusDays(60), 100);
        List<BatchStock> batches = List.of(late, early, middle);

        FefoAllocator.AllocationResult result = FefoAllocator.allocate(SKU, 6, batches);

        assertTrue(result.isSuccess());
        assertEquals(2, result.getDetails().size());
        assertEquals("B-EARLY", result.getDetails().get(0).getBatchNo());
        assertEquals(5, result.getDetails().get(0).getAllocatedQty());
        assertEquals("B-MID", result.getDetails().get(1).getBatchNo());
        assertEquals(1, result.getDetails().get(1).getAllocatedQty());
    }

    @Test
    @DisplayName("跨批次拆分数量正确: 总和等于请求数量")
    void crossBatchSplitSum() {
        LocalDate today = LocalDate.now();
        List<BatchStock> batches = List.of(
                batch("B1", today.plusDays(10), 10),
                batch("B2", today.plusDays(20), 20),
                batch("B3", today.plusDays(30), 30),
                batch("B4", today.plusDays(40), 40));

        int requestQty = 75;
        FefoAllocator.AllocationResult result = FefoAllocator.allocate(SKU, requestQty, batches);

        assertTrue(result.isSuccess());
        int totalAllocated = result.getDetails().stream()
                .mapToInt(AllocationDetail::getAllocatedQty).sum();
        assertEquals(requestQty, totalAllocated);
        assertEquals(4, result.getDetails().size());
        assertEquals(10, result.getDetails().get(0).getAllocatedQty());
        assertEquals(20, result.getDetails().get(1).getAllocatedQty());
        assertEquals(30, result.getDetails().get(2).getAllocatedQty());
        assertEquals(15, result.getDetails().get(3).getAllocatedQty());
    }

    @Test
    @DisplayName("可用量不足时返回失败并明确缺口数量")
    void shortageReportedCorrectly() {
        LocalDate today = LocalDate.now();
        List<BatchStock> batches = List.of(
                batch("B1", today.plusDays(10), 20),
                batch("B2", today.plusDays(20), 30));

        FefoAllocator.AllocationResult result = FefoAllocator.allocate(SKU, 100, batches);

        assertFalse(result.isSuccess());
        assertEquals(100, result.getRequestedQty());
        assertEquals(50, result.getTotalAvailable());
        assertEquals(50, result.getShortageQty());
    }

    @Test
    @DisplayName("同一天到期的批次按生产日期排序")
    void sameExpiryOrderByProduction() {
        LocalDate today = LocalDate.now();
        LocalDate expiry = today.plusDays(30);
        BatchStock laterProduction = batch("B-LATER", expiry, 50);
        laterProduction.setProductionDate(today.minusDays(50));
        laterProduction.setId(2L);
        BatchStock earlierProduction = batch("B-EARLIER", expiry, 50);
        earlierProduction.setProductionDate(today.minusDays(100));
        earlierProduction.setId(1L);

        List<BatchStock> batches = new ArrayList<>(List.of(laterProduction, earlierProduction));
        FefoAllocator.AllocationResult result = FefoAllocator.allocate(SKU, 60, batches);

        assertTrue(result.isSuccess());
        assertEquals("B-EARLIER", result.getDetails().get(0).getBatchNo());
        assertEquals(50, result.getDetails().get(0).getAllocatedQty());
        assertEquals("B-LATER", result.getDetails().get(1).getBatchNo());
        assertEquals(10, result.getDetails().get(1).getAllocatedQty());
    }

    @Test
    @DisplayName("跳过可用量为0的批次")
    void skipZeroAvailableBatches() {
        LocalDate today = LocalDate.now();
        List<BatchStock> batches = List.of(
                batch("B1", today.plusDays(5), 0),
                batch("B2", today.plusDays(10), 0),
                batch("B3", today.plusDays(15), 100));

        FefoAllocator.AllocationResult result = FefoAllocator.allocate(SKU, 50, batches);

        assertTrue(result.isSuccess());
        assertEquals(1, result.getDetails().size());
        assertEquals("B3", result.getDetails().get(0).getBatchNo());
    }

    @Test
    @DisplayName("请求数量为0或负数时抛异常")
    void invalidQtyThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> FefoAllocator.allocate(SKU, 0, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> FefoAllocator.allocate(SKU, -5, List.of()));
    }
}
