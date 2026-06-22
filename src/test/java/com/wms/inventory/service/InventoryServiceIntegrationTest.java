package com.wms.inventory.service;

import com.wms.inventory.dto.*;
import com.wms.inventory.entity.BatchInventory;
import com.wms.inventory.entity.InventoryTransaction;
import com.wms.inventory.entity.Sku;
import com.wms.inventory.exception.InventoryException;
import com.wms.inventory.repository.BatchInventoryRepository;
import com.wms.inventory.repository.InventoryTransactionRepository;
import com.wms.inventory.repository.SkuRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class InventoryServiceIntegrationTest {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private SkuService skuService;

    @Autowired
    private BatchInventoryRepository batchInventoryRepository;

    @Autowired
    private InventoryTransactionRepository transactionRepository;

    @Autowired
    private SkuRepository skuRepository;

    private static final String SKU_A = "TEST-SKU-A";
    private static final String SKU_B = "TEST-SKU-B";

    @BeforeEach
    void setup() {
        batchInventoryRepository.deleteAll();
        transactionRepository.deleteAll();
        skuRepository.deleteAll();

        SkuCreateRequest skuA = new SkuCreateRequest();
        skuA.setSkuCode(SKU_A);
        skuA.setSkuName("测试货品A");
        skuA.setShelfLifeDays(180);
        skuService.create(skuA);

        SkuCreateRequest skuB = new SkuCreateRequest();
        skuB.setSkuCode(SKU_B);
        skuB.setSkuName("测试货品B");
        skuB.setShelfLifeDays(365);
        skuService.create(skuB);
    }

    @Test
    @DisplayName("入库: 新建批次正确，同批次同库位累加")
    void inboundCreatesAndAccumulates() {
        LocalDate today = LocalDate.now();
        inbound(SKU_A, "BATCH-001", "LOC-A1", today.minusDays(10), today.plusDays(170), 100);
        inbound(SKU_A, "BATCH-001", "LOC-A1", today.minusDays(10), today.plusDays(170), 50);

        BatchInventory bi = batchInventoryRepository
                .findBySkuCodeAndBatchNoAndLocationCode(SKU_A, "BATCH-001", "LOC-A1")
                .orElseThrow();
        assertEquals(150, bi.getPhysicalQty());
        assertEquals(150, bi.getAvailableQty());
        assertEquals(0, bi.getReservedQty());
    }

    @Test
    @DisplayName("出库分配: FEFO 先扣最早到期，跨批次拆分正确")
    void outboundFefoWithSplit() {
        LocalDate today = LocalDate.now();
        inbound(SKU_A, "B-EARLY", "L1", today.minusDays(100), today.plusDays(10), 50);
        inbound(SKU_A, "B-MID", "L2", today.minusDays(80), today.plusDays(30), 100);
        inbound(SKU_A, "B-LATE", "L3", today.minusDays(50), today.plusDays(90), 200);

        OutboundAllocateRequest req = new OutboundAllocateRequest();
        req.setSkuCode(SKU_A);
        req.setQty(180);
        AllocationResultDTO result = inventoryService.allocateAndOutbound(req);

        assertTrue(result.isSuccess());
        assertEquals(180, result.getRequestedQty());
        assertEquals(3, result.getDetails().size());

        assertEquals("B-EARLY", result.getDetails().get(0).getBatchNo());
        assertEquals(50, result.getDetails().get(0).getAllocatedQty());
        assertEquals("B-MID", result.getDetails().get(1).getBatchNo());
        assertEquals(100, result.getDetails().get(1).getAllocatedQty());
        assertEquals("B-LATE", result.getDetails().get(2).getBatchNo());
        assertEquals(30, result.getDetails().get(2).getAllocatedQty());

        int total = batchInventoryRepository.sumPhysicalQtyBySkuCode(SKU_A);
        assertEquals(350 - 180, total);
        assertEquals(350 - 180, batchInventoryRepository.sumAvailableQtyBySkuCode(SKU_A));
    }

    @Test
    @DisplayName("出库: 可用量不足时不扣减任何库存，返回缺口数量")
    void outboundShortageRollsBack() {
        LocalDate today = LocalDate.now();
        inbound(SKU_A, "B1", "L1", today.minusDays(10), today.plusDays(170), 50);

        OutboundAllocateRequest req = new OutboundAllocateRequest();
        req.setSkuCode(SKU_A);
        req.setQty(100);
        AllocationResultDTO result = inventoryService.allocateAndOutbound(req);

        assertFalse(result.isSuccess());
        assertEquals(50, result.getShortageQty());

        assertEquals(50, batchInventoryRepository.sumPhysicalQtyBySkuCode(SKU_A));
        assertEquals(50, batchInventoryRepository.sumAvailableQtyBySkuCode(SKU_A));
    }

    @Test
    @DisplayName("预占与释放: 账目守恒，预占后可用量减少，释放后恢复")
    void reserveAndReleaseConservesLedger() {
        LocalDate today = LocalDate.now();
        inbound(SKU_A, "B1", "L1", today.minusDays(10), today.plusDays(170), 100);

        ReserveRequest rReq = new ReserveRequest();
        rReq.setSkuCode(SKU_A);
        rReq.setQty(40);
        rReq.setTimeoutMinutes(10);
        Map<String, Object> rResult = inventoryService.reserve(rReq);
        String resNo = (String) rResult.get("reservationNo");

        assertEquals(100, batchInventoryRepository.sumPhysicalQtyBySkuCode(SKU_A));
        assertEquals(40, batchInventoryRepository.sumReservedQtyBySkuCode(SKU_A));
        assertEquals(60, batchInventoryRepository.sumAvailableQtyBySkuCode(SKU_A));

        inventoryService.releaseReservation(resNo, "tester");

        assertEquals(100, batchInventoryRepository.sumPhysicalQtyBySkuCode(SKU_A));
        assertEquals(0, batchInventoryRepository.sumReservedQtyBySkuCode(SKU_A));
        assertEquals(100, batchInventoryRepository.sumAvailableQtyBySkuCode(SKU_A));
    }

    @Test
    @DisplayName("预占后确认拣货: 实物和预占同时减少")
    void reserveThenConfirm() {
        LocalDate today = LocalDate.now();
        inbound(SKU_A, "B1", "L1", today.minusDays(10), today.plusDays(170), 100);

        ReserveRequest rReq = new ReserveRequest();
        rReq.setSkuCode(SKU_A);
        rReq.setQty(40);
        rReq.setTimeoutMinutes(10);
        String resNo = (String) inventoryService.reserve(rReq).get("reservationNo");

        inventoryService.confirmReservation(resNo, "tester");

        assertEquals(60, batchInventoryRepository.sumPhysicalQtyBySkuCode(SKU_A));
        assertEquals(0, batchInventoryRepository.sumReservedQtyBySkuCode(SKU_A));
        assertEquals(60, batchInventoryRepository.sumAvailableQtyBySkuCode(SKU_A));
    }

    @Test
    @DisplayName("重复释放抛异常")
    void doubleReleaseThrows() {
        LocalDate today = LocalDate.now();
        inbound(SKU_A, "B1", "L1", today.minusDays(10), today.plusDays(170), 100);

        ReserveRequest rReq = new ReserveRequest();
        rReq.setSkuCode(SKU_A);
        rReq.setQty(40);
        String resNo = (String) inventoryService.reserve(rReq).get("reservationNo");

        inventoryService.releaseReservation(resNo, "tester");
        assertThrows(InventoryException.class,
                () -> inventoryService.releaseReservation(resNo, "tester"));
    }

    @Test
    @DisplayName("调拨: 数量守恒，源库位减少目标库位增加")
    void transferConservesQuantity() {
        LocalDate today = LocalDate.now();
        inbound(SKU_A, "B1", "LOC-FROM", today.minusDays(10), today.plusDays(170), 100);

        TransferRequest tReq = new TransferRequest();
        tReq.setSkuCode(SKU_A);
        tReq.setBatchNo("B1");
        tReq.setFromLocation("LOC-FROM");
        tReq.setToLocation("LOC-TO");
        tReq.setQty(40);
        inventoryService.transfer(tReq);

        BatchInventory from = batchInventoryRepository
                .findBySkuCodeAndBatchNoAndLocationCode(SKU_A, "B1", "LOC-FROM").orElseThrow();
        BatchInventory to = batchInventoryRepository
                .findBySkuCodeAndBatchNoAndLocationCode(SKU_A, "B1", "LOC-TO").orElseThrow();

        assertEquals(60, from.getPhysicalQty());
        assertEquals(60, from.getAvailableQty());
        assertEquals(40, to.getPhysicalQty());
        assertEquals(40, to.getAvailableQty());
        assertEquals(100, from.getPhysicalQty() + to.getPhysicalQty());
    }

    @Test
    @DisplayName("临期预警: 按阈值正确筛选")
    void expiryAlertFiltersByThreshold() {
        LocalDate today = LocalDate.now();
        inbound(SKU_A, "B-EXPIRED", "L1", today.minusDays(200), today.minusDays(5), 10);
        inbound(SKU_A, "B-NEAR", "L2", today.minusDays(150), today.plusDays(10), 20);
        inbound(SKU_A, "B-NORMAL", "L3", today.minusDays(10), today.plusDays(200), 30);

        List<ExpiryAlertDTO> alerts = inventoryService.getExpiryAlerts(30, SKU_A);

        assertEquals(2, alerts.size());
        assertTrue(alerts.stream().anyMatch(a -> a.getBatchNo().equals("B-EXPIRED")));
        assertTrue(alerts.stream().anyMatch(a -> a.getBatchNo().equals("B-NEAR")));
        assertFalse(alerts.stream().anyMatch(a -> a.getBatchNo().equals("B-NORMAL")));
    }

    @Test
    @DisplayName("每笔操作都留流水")
    void everyOperationHasTransaction() {
        LocalDate today = LocalDate.now();
        inbound(SKU_A, "B1", "L1", today.minusDays(10), today.plusDays(170), 100);

        OutboundAllocateRequest oReq = new OutboundAllocateRequest();
        oReq.setSkuCode(SKU_A);
        oReq.setQty(20);
        inventoryService.allocateAndOutbound(oReq);

        List<InventoryTransaction> txns = transactionRepository.findBySkuCodeOrderByCreatedAtDesc(SKU_A);
        assertTrue(txns.size() >= 2);
        assertTrue(txns.stream().anyMatch(t -> "INBOUND".equals(t.getTxnType())));
        assertTrue(txns.stream().anyMatch(t -> "OUTBOUND".equals(t.getTxnType())));
    }

    @Test
    @DisplayName("并发出库不超卖: N个线程各抢部分库存，总量不超过可用量")
    void concurrentOutboundNoOversell() throws Exception {
        int threadCount = 10;
        int perThreadQty = 20;
        int initialQty = 150;

        LocalDate today = LocalDate.now();
        inbound(SKU_B, "B-CONCURRENT", "L1", today.minusDays(10), today.plusDays(100), initialQty);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                try {
                    startLatch.await();
                    OutboundAllocateRequest req = new OutboundAllocateRequest();
                    req.setSkuCode(SKU_B);
                    req.setQty(perThreadQty);
                    AllocationResultDTO res = inventoryService.allocateAndOutbound(req);
                    if (res.isSuccess()) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            }));
        }

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        int physicalLeft = batchInventoryRepository.sumPhysicalQtyBySkuCode(SKU_B);
        assertTrue(physicalLeft >= 0, "实物量不能为负");
        assertTrue(physicalLeft <= initialQty, "实物量不能超过初始值");

        int successQty = successCount.get() * perThreadQty;
        assertEquals(initialQty, successQty + physicalLeft,
                "已出库量 + 剩余量 = 初始量");

        int expectedSuccess = initialQty / perThreadQty;
        assertEquals(expectedSuccess, successCount.get(), "成功次数应匹配整除结果");
    }

    @Test
    @DisplayName("库存汇总: 三本账分开统计正确")
    void inventorySummaryThreeLedgers() {
        LocalDate today = LocalDate.now();
        inbound(SKU_A, "B1", "L1", today.minusDays(10), today.plusDays(170), 100);

        ReserveRequest rReq = new ReserveRequest();
        rReq.setSkuCode(SKU_A);
        rReq.setQty(30);
        inventoryService.reserve(rReq);

        Map<String, Object> summary = inventoryService.getInventorySummary(SKU_A);
        assertEquals(100, summary.get("physicalQty"));
        assertEquals(30, summary.get("reservedQty"));
        assertEquals(70, summary.get("availableQty"));
    }

    private void inbound(String sku, String batch, String loc,
                         LocalDate prod, LocalDate expiry, int qty) {
        InboundRequest req = new InboundRequest();
        req.setSkuCode(sku);
        req.setBatchNo(batch);
        req.setLocationCode(loc);
        req.setProductionDate(prod);
        req.setExpiryDate(expiry);
        req.setQty(qty);
        req.setOperator("test");
        inventoryService.inbound(req);
    }
}
