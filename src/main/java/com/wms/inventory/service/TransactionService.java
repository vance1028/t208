package com.wms.inventory.service;

import com.wms.inventory.core.InventoryLedger;
import com.wms.inventory.core.enums.TxnType;
import com.wms.inventory.entity.InventoryTransaction;
import com.wms.inventory.repository.InventoryTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final InventoryTransactionRepository transactionRepository;
    private static final DateTimeFormatter TXN_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public InventoryTransaction recordInbound(String skuCode, String batchNo, String locationCode,
                                              int qty, InventoryLedger.LedgerSnapshot snapshot,
                                              String refNo, String operator, String remark) {
        return createTransaction(TxnType.INBOUND, skuCode, batchNo, locationCode,
                qty, snapshot, refNo, operator, remark);
    }

    public InventoryTransaction recordOutbound(String skuCode, String batchNo, String locationCode,
                                               int qty, InventoryLedger.LedgerSnapshot snapshot,
                                               String refNo, String operator, String remark) {
        return createTransaction(TxnType.OUTBOUND, skuCode, batchNo, locationCode,
                -qty, snapshot, refNo, operator, remark);
    }

    public InventoryTransaction recordReserve(String skuCode, String batchNo, String locationCode,
                                              int qty, InventoryLedger.LedgerSnapshot snapshot,
                                              String refNo, String operator, String remark) {
        return createTransaction(TxnType.RESERVE, skuCode, batchNo, locationCode,
                -qty, snapshot, refNo, operator, remark);
    }

    public InventoryTransaction recordRelease(String skuCode, String batchNo, String locationCode,
                                              int qty, InventoryLedger.LedgerSnapshot snapshot,
                                              String refNo, String operator, String remark) {
        return createTransaction(TxnType.RELEASE, skuCode, batchNo, locationCode,
                qty, snapshot, refNo, operator, remark);
    }

    public InventoryTransaction recordConfirm(String skuCode, String batchNo, String locationCode,
                                              int qty, InventoryLedger.LedgerSnapshot snapshot,
                                              String refNo, String operator, String remark) {
        return createTransaction(TxnType.CONFIRM, skuCode, batchNo, locationCode,
                -qty, snapshot, refNo, operator, remark);
    }

    public InventoryTransaction recordTransferIn(String skuCode, String batchNo, String locationCode,
                                                 int qty, InventoryLedger.LedgerSnapshot snapshot,
                                                 String refNo, String operator, String remark) {
        return createTransaction(TxnType.TRANSFER_IN, skuCode, batchNo, locationCode,
                qty, snapshot, refNo, operator, remark);
    }

    public InventoryTransaction recordTransferOut(String skuCode, String batchNo, String locationCode,
                                                  int qty, InventoryLedger.LedgerSnapshot snapshot,
                                                  String refNo, String operator, String remark) {
        return createTransaction(TxnType.TRANSFER_OUT, skuCode, batchNo, locationCode,
                -qty, snapshot, refNo, operator, remark);
    }

    private InventoryTransaction createTransaction(TxnType type, String skuCode, String batchNo,
                                                   String locationCode, int qtyChange,
                                                   InventoryLedger.LedgerSnapshot snapshot,
                                                   String refNo, String operator, String remark) {
        InventoryTransaction txn = new InventoryTransaction();
        txn.setTxnNo(generateTxnNo());
        txn.setTxnType(type.name());
        txn.setRefNo(refNo);
        txn.setSkuCode(skuCode);
        txn.setBatchNo(batchNo);
        txn.setLocationCode(locationCode);
        txn.setQtyChange(qtyChange);
        if (snapshot != null) {
            txn.setPhysicalBefore(snapshot.getPhysicalBefore());
            txn.setPhysicalAfter(snapshot.getPhysicalAfter());
            txn.setReservedBefore(snapshot.getReservedBefore());
            txn.setReservedAfter(snapshot.getReservedAfter());
            txn.setAvailableBefore(snapshot.getAvailableBefore());
            txn.setAvailableAfter(snapshot.getAvailableAfter());
        }
        txn.setOperator(operator);
        txn.setRemark(remark);
        return transactionRepository.save(txn);
    }

    private String generateTxnNo() {
        return "TX" + LocalDateTime.now().format(TXN_NO_FORMATTER)
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }
}
