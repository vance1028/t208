package com.wms.inventory.service;

import com.wms.inventory.core.FefoAllocator;
import com.wms.inventory.core.InventoryLedger;
import com.wms.inventory.core.enums.ReservationStatus;
import com.wms.inventory.core.enums.TxnType;
import com.wms.inventory.core.model.AllocationDetail;
import com.wms.inventory.core.model.BatchStock;
import com.wms.inventory.dto.*;
import com.wms.inventory.entity.*;
import com.wms.inventory.exception.InventoryException;
import com.wms.inventory.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final BatchInventoryRepository batchInventoryRepository;
    private final SkuRepository skuRepository;
    private final InventoryReservationRepository reservationRepository;
    private final InventoryReservationItemRepository reservationItemRepository;
    private final InventoryTransferRepository transferRepository;
    private final TransactionService transactionService;

    @Value("${inventory.reservation.default-timeout-minutes:30}")
    private int defaultTimeoutMinutes;

    private static final DateTimeFormatter RESERVATION_NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter TRANSFER_NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Transactional
    public BatchInventory inbound(InboundRequest request) {
        if (request.getProductionDate().isAfter(request.getExpiryDate())) {
            throw new InventoryException("生产日期不能晚于到期日期");
        }
        if (!skuRepository.existsBySkuCode(request.getSkuCode())) {
            throw new InventoryException("货品不存在: " + request.getSkuCode());
        }

        Optional<BatchInventory> existingOpt = batchInventoryRepository
                .findBySkuCodeAndBatchNoAndLocationCodeForUpdate(
                        request.getSkuCode(), request.getBatchNo(), request.getLocationCode());

        BatchInventory inventory;
        if (existingOpt.isPresent()) {
            inventory = existingOpt.get();
            BatchStock stock = toBatchStock(inventory);
            InventoryLedger.LedgerSnapshot snapshot = InventoryLedger.inbound(stock, request.getQty());
            inventory.setPhysicalQty(stock.getPhysicalQty());
            inventory.setReservedQty(stock.getReservedQty());
            inventory.setAvailableQty(stock.getAvailableQty());
            inventory = batchInventoryRepository.save(inventory);
            transactionService.recordInbound(
                    request.getSkuCode(), request.getBatchNo(), request.getLocationCode(),
                    request.getQty(), snapshot, null, request.getOperator(), request.getRemark());
        } else {
            inventory = new BatchInventory();
            inventory.setSkuCode(request.getSkuCode());
            inventory.setBatchNo(request.getBatchNo());
            inventory.setLocationCode(request.getLocationCode());
            inventory.setProductionDate(request.getProductionDate());
            inventory.setExpiryDate(request.getExpiryDate());
            inventory.setInboundTime(LocalDateTime.now());
            inventory.setPhysicalQty(request.getQty());
            inventory.setReservedQty(0);
            inventory.setAvailableQty(request.getQty());
            inventory = batchInventoryRepository.save(inventory);
            InventoryLedger.LedgerSnapshot snapshot = new InventoryLedger.LedgerSnapshot(
                    0, request.getQty(), 0, 0, 0, request.getQty());
            transactionService.recordInbound(
                    request.getSkuCode(), request.getBatchNo(), request.getLocationCode(),
                    request.getQty(), snapshot, null, request.getOperator(), request.getRemark());
        }
        log.info("入库成功: sku={}, batch={}, location={}, qty={}",
                request.getSkuCode(), request.getBatchNo(), request.getLocationCode(), request.getQty());
        return inventory;
    }

    @Transactional(readOnly = true)
    public AllocationResultDTO previewAllocation(String skuCode, int qty) {
        if (!skuRepository.existsBySkuCode(skuCode)) {
            throw new InventoryException("货品不存在: " + skuCode);
        }
        List<BatchInventory> batches = batchInventoryRepository.findAvailableBatchesOrderByExpiry(skuCode);
        List<BatchStock> stocks = batches.stream().map(this::toBatchStock).collect(Collectors.toList());
        FefoAllocator.AllocationResult result = FefoAllocator.allocate(skuCode, qty, stocks);
        return toAllocationResultDTO(result);
    }

    @Transactional
    public AllocationResultDTO allocateAndOutbound(OutboundAllocateRequest request) {
        if (!skuRepository.existsBySkuCode(request.getSkuCode())) {
            throw new InventoryException("货品不存在: " + request.getSkuCode());
        }

        List<BatchInventory> batches = batchInventoryRepository
                .findAvailableBatchesOrderByExpiryForUpdate(request.getSkuCode());
        List<BatchStock> stocks = batches.stream().map(this::toBatchStock).collect(Collectors.toList());

        FefoAllocator.AllocationResult result = FefoAllocator.allocate(request.getSkuCode(), request.getQty(), stocks);
        AllocationResultDTO dto = toAllocationResultDTO(result);

        if (!result.isSuccess()) {
            dto.setMessage(String.format("可用量不足, 需求%d, 可用%d, 缺%d",
                    result.getRequestedQty(), result.getTotalAvailable(), result.getShortageQty()));
            return dto;
        }

        Map<Long, BatchInventory> inventoryMap = batches.stream()
                .collect(Collectors.toMap(BatchInventory::getId, b -> b));

        for (AllocationDetail detail : result.getDetails()) {
            BatchInventory bi = findInventoryByDetail(inventoryMap, detail);
            BatchStock stock = toBatchStock(bi);
            InventoryLedger.LedgerSnapshot snapshot = InventoryLedger.outbound(stock, detail.getAllocatedQty());
            bi.setPhysicalQty(stock.getPhysicalQty());
            bi.setAvailableQty(stock.getAvailableQty());
            batchInventoryRepository.save(bi);
            transactionService.recordOutbound(
                    detail.getSkuCode(), detail.getBatchNo(), detail.getLocationCode(),
                    detail.getAllocatedQty(), snapshot, request.getOrderNo(), request.getOperator(), request.getRemark());
        }

        dto.setMessage("出库成功");
        log.info("FEFO出库成功: sku={}, qty={}, 拆分为{}个批次",
                request.getSkuCode(), request.getQty(), result.getDetails().size());
        return dto;
    }

    @Transactional
    public Map<String, Object> reserve(ReserveRequest request) {
        if (!skuRepository.existsBySkuCode(request.getSkuCode())) {
            throw new InventoryException("货品不存在: " + request.getSkuCode());
        }
        if (request.getOrderNo() != null && reservationRepository.existsByOrderNoAndStatus(
                request.getOrderNo(), ReservationStatus.ACTIVE.name())) {
            throw new InventoryException("该订单已有生效预占，请先释放");
        }

        List<BatchInventory> batches = batchInventoryRepository
                .findAvailableBatchesOrderByExpiryForUpdate(request.getSkuCode());
        List<BatchStock> stocks = batches.stream().map(this::toBatchStock).collect(Collectors.toList());

        FefoAllocator.AllocationResult allocation = FefoAllocator.allocate(request.getSkuCode(), request.getQty(), stocks);
        if (!allocation.isSuccess()) {
            throw new InventoryException(String.format(
                    "可用量不足, 无法预占: 需求%d, 可用%d, 缺%d",
                    allocation.getRequestedQty(), allocation.getTotalAvailable(), allocation.getShortageQty()));
        }

        LocalDateTime expireAt = request.getExpireAt() != null ? request.getExpireAt()
                : LocalDateTime.now().plusMinutes(
                        request.getTimeoutMinutes() != null ? request.getTimeoutMinutes() : defaultTimeoutMinutes);

        String reservationNo = "RES" + LocalDateTime.now().format(RESERVATION_NO_FMT)
                + UUID.randomUUID().toString().substring(0, 4).toUpperCase();

        InventoryReservation reservation = new InventoryReservation();
        reservation.setReservationNo(reservationNo);
        reservation.setOrderNo(request.getOrderNo());
        reservation.setStatus(ReservationStatus.ACTIVE.name());
        reservation.setExpireAt(expireAt);
        reservation.setRemark(request.getRemark());
        reservation = reservationRepository.save(reservation);

        Map<Long, BatchInventory> inventoryMap = batches.stream()
                .collect(Collectors.toMap(BatchInventory::getId, b -> b));

        List<InventoryReservationItem> items = new ArrayList<>();
        for (AllocationDetail detail : allocation.getDetails()) {
            BatchInventory bi = findInventoryByDetail(inventoryMap, detail);
            BatchStock stock = toBatchStock(bi);
            InventoryLedger.LedgerSnapshot snapshot = InventoryLedger.reserve(stock, detail.getAllocatedQty());
            bi.setReservedQty(stock.getReservedQty());
            bi.setAvailableQty(stock.getAvailableQty());
            batchInventoryRepository.save(bi);

            InventoryReservationItem item = new InventoryReservationItem();
            item.setReservationId(reservation.getId());
            item.setSkuCode(detail.getSkuCode());
            item.setBatchNo(detail.getBatchNo());
            item.setLocationCode(detail.getLocationCode());
            item.setReservedQty(detail.getAllocatedQty());
            items.add(reservationItemRepository.save(item));

            transactionService.recordReserve(
                    detail.getSkuCode(), detail.getBatchNo(), detail.getLocationCode(),
                    detail.getAllocatedQty(), snapshot, reservationNo, request.getOperator(), request.getRemark());
        }

        log.info("预占成功: reservationNo={}, sku={}, qty={}, 拆分为{}个批次",
                reservationNo, request.getSkuCode(), request.getQty(), items.size());

        Map<String, Object> result = new HashMap<>();
        result.put("reservationNo", reservationNo);
        result.put("status", ReservationStatus.ACTIVE.name());
        result.put("expireAt", expireAt);
        result.put("details", allocation.getDetails());
        return result;
    }

    @Transactional
    public void releaseReservation(String reservationNo, String operator) {
        InventoryReservation reservation = reservationRepository.findByReservationNo(reservationNo)
                .orElseThrow(() -> new InventoryException("预占单不存在: " + reservationNo));

        if (!ReservationStatus.ACTIVE.name().equals(reservation.getStatus())) {
            throw new InventoryException("预占单状态不是ACTIVE，无法释放，当前状态: " + reservation.getStatus());
        }

        List<InventoryReservationItem> items = reservationItemRepository.findByReservationId(reservation.getId());
        for (InventoryReservationItem item : items) {
            BatchInventory bi = batchInventoryRepository
                    .findBySkuCodeAndBatchNoAndLocationCodeForUpdate(
                            item.getSkuCode(), item.getBatchNo(), item.getLocationCode())
                    .orElseThrow(() -> new InventoryException(String.format(
                            "库存记录不存在: sku=%s, batch=%s, location=%s",
                            item.getSkuCode(), item.getBatchNo(), item.getLocationCode())));
            BatchStock stock = toBatchStock(bi);
            InventoryLedger.LedgerSnapshot snapshot = InventoryLedger.release(stock, item.getReservedQty());
            bi.setReservedQty(stock.getReservedQty());
            bi.setAvailableQty(stock.getAvailableQty());
            batchInventoryRepository.save(bi);
            transactionService.recordRelease(
                    item.getSkuCode(), item.getBatchNo(), item.getLocationCode(),
                    item.getReservedQty(), snapshot, reservationNo, operator, "释放预占");
        }

        reservation.setStatus(ReservationStatus.RELEASED.name());
        reservationRepository.save(reservation);
        log.info("预占释放成功: reservationNo={}", reservationNo);
    }

    @Transactional
    public void confirmReservation(String reservationNo, String operator) {
        InventoryReservation reservation = reservationRepository.findByReservationNo(reservationNo)
                .orElseThrow(() -> new InventoryException("预占单不存在: " + reservationNo));

        if (!ReservationStatus.ACTIVE.name().equals(reservation.getStatus())) {
            throw new InventoryException("预占单状态不是ACTIVE，无法确认拣货，当前状态: " + reservation.getStatus());
        }

        List<InventoryReservationItem> items = reservationItemRepository.findByReservationId(reservation.getId());
        for (InventoryReservationItem item : items) {
            BatchInventory bi = batchInventoryRepository
                    .findBySkuCodeAndBatchNoAndLocationCodeForUpdate(
                            item.getSkuCode(), item.getBatchNo(), item.getLocationCode())
                    .orElseThrow(() -> new InventoryException(String.format(
                            "库存记录不存在: sku=%s, batch=%s, location=%s",
                            item.getSkuCode(), item.getBatchNo(), item.getLocationCode())));
            BatchStock stock = toBatchStock(bi);
            InventoryLedger.LedgerSnapshot snapshot = InventoryLedger.confirmReservation(stock, item.getReservedQty());
            bi.setPhysicalQty(stock.getPhysicalQty());
            bi.setReservedQty(stock.getReservedQty());
            bi.setAvailableQty(stock.getAvailableQty());
            batchInventoryRepository.save(bi);
            transactionService.recordConfirm(
                    item.getSkuCode(), item.getBatchNo(), item.getLocationCode(),
                    item.getReservedQty(), snapshot, reservationNo, operator, "确认拣货出库");
        }

        reservation.setStatus(ReservationStatus.CONFIRMED.name());
        reservationRepository.save(reservation);
        log.info("预占确认拣货成功: reservationNo={}", reservationNo);
    }

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void releaseExpiredReservations() {
        List<InventoryReservation> expired = reservationRepository
                .findExpiredReservations(LocalDateTime.now());
        for (InventoryReservation r : expired) {
            try {
                r.setStatus(ReservationStatus.EXPIRED.name());
                reservationRepository.save(r);
                List<InventoryReservationItem> items = reservationItemRepository.findByReservationId(r.getId());
                for (InventoryReservationItem item : items) {
                    Optional<BatchInventory> biOpt = batchInventoryRepository
                            .findBySkuCodeAndBatchNoAndLocationCodeForUpdate(
                                    item.getSkuCode(), item.getBatchNo(), item.getLocationCode());
                    if (biOpt.isPresent()) {
                        BatchInventory bi = biOpt.get();
                        BatchStock stock = toBatchStock(bi);
                        InventoryLedger.release(stock, item.getReservedQty());
                        bi.setReservedQty(stock.getReservedQty());
                        bi.setAvailableQty(stock.getAvailableQty());
                        batchInventoryRepository.save(bi);
                    }
                }
                log.info("超时自动释放预占: reservationNo={}", r.getReservationNo());
            } catch (Exception e) {
                log.error("释放超时预占失败: reservationNo={}", r.getReservationNo(), e);
            }
        }
    }

    @Transactional
    public Map<String, Object> transfer(TransferRequest request) {
        if (request.getFromLocation().equals(request.getToLocation())) {
            throw new InventoryException("源库位和目标库位不能相同");
        }
        if (!skuRepository.existsBySkuCode(request.getSkuCode())) {
            throw new InventoryException("货品不存在: " + request.getSkuCode());
        }

        BatchInventory fromBi = batchInventoryRepository
                .findBySkuCodeAndBatchNoAndLocationCodeForUpdate(
                        request.getSkuCode(), request.getBatchNo(), request.getFromLocation())
                .orElseThrow(() -> new InventoryException(String.format(
                        "源库位库存不存在: sku=%s, batch=%s, location=%s",
                        request.getSkuCode(), request.getBatchNo(), request.getFromLocation())));

        if (fromBi.getAvailableQty() < request.getQty()) {
            throw new InventoryException(String.format(
                    "源库位可用量不足: 可用%d, 需调拨%d", fromBi.getAvailableQty(), request.getQty()));
        }

        BatchStock fromStock = toBatchStock(fromBi);
        int fromPhysBefore = fromStock.getPhysicalQty();
        int fromAvailBefore = fromStock.getAvailableQty();
        InventoryLedger.transferOut(fromStock, request.getQty());
        fromBi.setPhysicalQty(fromStock.getPhysicalQty());
        fromBi.setAvailableQty(fromStock.getAvailableQty());
        batchInventoryRepository.save(fromBi);
        InventoryLedger.LedgerSnapshot outSnapshot = new InventoryLedger.LedgerSnapshot(
                fromPhysBefore, fromStock.getPhysicalQty(),
                fromStock.getReservedQty(), fromStock.getReservedQty(),
                fromAvailBefore, fromStock.getAvailableQty());

        Optional<BatchInventory> toBiOpt = batchInventoryRepository
                .findBySkuCodeAndBatchNoAndLocationCodeForUpdate(
                        request.getSkuCode(), request.getBatchNo(), request.getToLocation());

        BatchInventory toBi;
        InventoryLedger.LedgerSnapshot inSnapshot;
        if (toBiOpt.isPresent()) {
            toBi = toBiOpt.get();
            BatchStock toStock = toBatchStock(toBi);
            int toPhysBefore = toStock.getPhysicalQty();
            int toAvailBefore = toStock.getAvailableQty();
            InventoryLedger.transferIn(toStock, request.getQty());
            toBi.setPhysicalQty(toStock.getPhysicalQty());
            toBi.setAvailableQty(toStock.getAvailableQty());
            toBi = batchInventoryRepository.save(toBi);
            inSnapshot = new InventoryLedger.LedgerSnapshot(
                    toPhysBefore, toStock.getPhysicalQty(),
                    toStock.getReservedQty(), toStock.getReservedQty(),
                    toAvailBefore, toStock.getAvailableQty());
        } else {
            toBi = new BatchInventory();
            toBi.setSkuCode(request.getSkuCode());
            toBi.setBatchNo(request.getBatchNo());
            toBi.setLocationCode(request.getToLocation());
            toBi.setProductionDate(fromBi.getProductionDate());
            toBi.setExpiryDate(fromBi.getExpiryDate());
            toBi.setInboundTime(LocalDateTime.now());
            toBi.setPhysicalQty(request.getQty());
            toBi.setReservedQty(0);
            toBi.setAvailableQty(request.getQty());
            toBi = batchInventoryRepository.save(toBi);
            inSnapshot = new InventoryLedger.LedgerSnapshot(
                    0, request.getQty(), 0, 0, 0, request.getQty());
        }

        String transferNo = "TR" + LocalDateTime.now().format(TRANSFER_NO_FMT)
                + UUID.randomUUID().toString().substring(0, 4).toUpperCase();

        InventoryTransfer transfer = new InventoryTransfer();
        transfer.setTransferNo(transferNo);
        transfer.setSkuCode(request.getSkuCode());
        transfer.setBatchNo(request.getBatchNo());
        transfer.setFromLocation(request.getFromLocation());
        transfer.setToLocation(request.getToLocation());
        transfer.setQty(request.getQty());
        transfer.setRemark(request.getRemark());
        transferRepository.save(transfer);

        transactionService.recordTransferOut(
                request.getSkuCode(), request.getBatchNo(), request.getFromLocation(),
                request.getQty(), outSnapshot, transferNo, request.getOperator(), request.getRemark());
        transactionService.recordTransferIn(
                request.getSkuCode(), request.getBatchNo(), request.getToLocation(),
                request.getQty(), inSnapshot, transferNo, request.getOperator(), request.getRemark());

        log.info("调拨成功: transferNo={}, {} -> {}, qty={}",
                transferNo, request.getFromLocation(), request.getToLocation(), request.getQty());

        Map<String, Object> result = new HashMap<>();
        result.put("transferNo", transferNo);
        result.put("status", "COMPLETED");
        result.put("qty", request.getQty());
        return result;
    }

    public List<ExpiryAlertDTO> getExpiryAlerts(Integer thresholdDays, String skuCode) {
        int days = thresholdDays != null ? thresholdDays : 30;
        if (days < 0) {
            throw new InventoryException("阈值天数不能为负");
        }
        LocalDate thresholdDate = LocalDate.now().plusDays(days);
        List<BatchInventory> batches;
        if (skuCode != null && !skuCode.isBlank()) {
            batches = batchInventoryRepository.findNearExpiryBatchesBySku(skuCode, thresholdDate);
        } else {
            batches = batchInventoryRepository.findNearExpiryBatches(thresholdDate);
        }
        LocalDate today = LocalDate.now();
        Map<String, String> skuNameCache = new HashMap<>();
        return batches.stream()
                .filter(b -> b.getPhysicalQty() > 0 || b.getReservedQty() > 0)
                .map(b -> {
                    String skuName = skuNameCache.computeIfAbsent(b.getSkuCode(),
                            code -> skuRepository.findBySkuCode(code).map(Sku::getSkuName).orElse(""));
                    long daysLeft = com.wms.inventory.core.ExpiryChecker.daysUntilExpiry(b.getExpiryDate(), today);
                    String status = com.wms.inventory.core.ExpiryChecker.getStatus(b.getExpiryDate(), today, days).getLabel();
                    return ExpiryAlertDTO.builder()
                            .skuCode(b.getSkuCode())
                            .skuName(skuName)
                            .batchNo(b.getBatchNo())
                            .locationCode(b.getLocationCode())
                            .productionDate(b.getProductionDate())
                            .expiryDate(b.getExpiryDate())
                            .physicalQty(b.getPhysicalQty())
                            .reservedQty(b.getReservedQty())
                            .availableQty(b.getAvailableQty())
                            .daysUntilExpiry(daysLeft)
                            .expiryStatus(status)
                            .build();
                })
                .collect(Collectors.toList());
    }

    public Map<String, Object> getInventorySummary(String skuCode) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("skuCode", skuCode);
        summary.put("physicalQty", batchInventoryRepository.sumPhysicalQtyBySkuCode(skuCode));
        summary.put("reservedQty", batchInventoryRepository.sumReservedQtyBySkuCode(skuCode));
        summary.put("availableQty", batchInventoryRepository.sumAvailableQtyBySkuCode(skuCode));
        summary.put("batches", batchInventoryRepository.findBySkuCode(skuCode));
        return summary;
    }

    private BatchStock toBatchStock(BatchInventory bi) {
        return BatchStock.builder()
                .id(bi.getId())
                .skuCode(bi.getSkuCode())
                .batchNo(bi.getBatchNo())
                .locationCode(bi.getLocationCode())
                .productionDate(bi.getProductionDate())
                .expiryDate(bi.getExpiryDate())
                .physicalQty(bi.getPhysicalQty())
                .reservedQty(bi.getReservedQty())
                .availableQty(bi.getAvailableQty())
                .version(bi.getVersion())
                .build();
    }

    private BatchInventory findInventoryByDetail(Map<Long, BatchInventory> inventoryMap, AllocationDetail detail) {
        return inventoryMap.values().stream()
                .filter(bi -> bi.getSkuCode().equals(detail.getSkuCode())
                        && bi.getBatchNo().equals(detail.getBatchNo())
                        && bi.getLocationCode().equals(detail.getLocationCode()))
                .findFirst()
                .orElseThrow(() -> new InventoryException("未找到对应库存记录"));
    }

    private AllocationResultDTO toAllocationResultDTO(FefoAllocator.AllocationResult result) {
        return AllocationResultDTO.builder()
                .success(result.isSuccess())
                .requestedQty(result.getRequestedQty())
                .totalAvailable(result.getTotalAvailable())
                .shortageQty(result.getShortageQty())
                .details(result.getDetails())
                .message(result.isSuccess() ? "分配成功" : "可用量不足")
                .build();
    }
}
