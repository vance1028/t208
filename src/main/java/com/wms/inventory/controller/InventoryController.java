package com.wms.inventory.controller;

import com.wms.inventory.dto.*;
import com.wms.inventory.entity.BatchInventory;
import com.wms.inventory.entity.InventoryTransaction;
import com.wms.inventory.repository.InventoryTransactionRepository;
import com.wms.inventory.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;
    private final InventoryTransactionRepository transactionRepository;

    @PostMapping("/inbound")
    public ApiResponse<BatchInventory> inbound(@RequestBody @Valid InboundRequest request) {
        return ApiResponse.success(inventoryService.inbound(request));
    }

    @GetMapping("/allocate/preview")
    public ApiResponse<AllocationResultDTO> previewAllocation(
            @RequestParam String skuCode,
            @RequestParam int qty) {
        return ApiResponse.success(inventoryService.previewAllocation(skuCode, qty));
    }

    @PostMapping("/outbound")
    public ApiResponse<AllocationResultDTO> allocateAndOutbound(
            @RequestBody @Valid OutboundAllocateRequest request) {
        return ApiResponse.success(inventoryService.allocateAndOutbound(request));
    }

    @PostMapping("/reserve")
    public ApiResponse<Map<String, Object>> reserve(@RequestBody @Valid ReserveRequest request) {
        return ApiResponse.success(inventoryService.reserve(request));
    }

    @PostMapping("/reserve/{reservationNo}/release")
    public ApiResponse<Void> releaseReservation(
            @PathVariable String reservationNo,
            @RequestParam(required = false) String operator) {
        inventoryService.releaseReservation(reservationNo, operator);
        return ApiResponse.success();
    }

    @PostMapping("/reserve/{reservationNo}/confirm")
    public ApiResponse<Void> confirmReservation(
            @PathVariable String reservationNo,
            @RequestParam(required = false) String operator) {
        inventoryService.confirmReservation(reservationNo, operator);
        return ApiResponse.success();
    }

    @PostMapping("/transfer")
    public ApiResponse<Map<String, Object>> transfer(@RequestBody @Valid TransferRequest request) {
        return ApiResponse.success(inventoryService.transfer(request));
    }

    @GetMapping("/expiry-alert")
    public ApiResponse<List<ExpiryAlertDTO>> getExpiryAlerts(
            @RequestParam(required = false) Integer thresholdDays,
            @RequestParam(required = false) String skuCode) {
        return ApiResponse.success(inventoryService.getExpiryAlerts(thresholdDays, skuCode));
    }

    @GetMapping("/summary/{skuCode}")
    public ApiResponse<Map<String, Object>> getInventorySummary(@PathVariable String skuCode) {
        return ApiResponse.success(inventoryService.getInventorySummary(skuCode));
    }

    @GetMapping("/transactions")
    public ApiResponse<Page<InventoryTransaction>> getTransactions(
            @RequestParam(required = false) String skuCode,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<InventoryTransaction> page;
        if (skuCode != null && !skuCode.isBlank()) {
            page = transactionRepository.findBySkuCodeOrderByCreatedAtDesc(skuCode, pageable);
        } else {
            page = transactionRepository.findAllByOrderByCreatedAtDesc(pageable);
        }
        return ApiResponse.success(page);
    }

    @GetMapping("/transactions/by-ref/{refNo}")
    public ApiResponse<List<InventoryTransaction>> getTransactionsByRef(@PathVariable String refNo) {
        return ApiResponse.success(transactionRepository.findByRefNoOrderByCreatedAtDesc(refNo));
    }
}
