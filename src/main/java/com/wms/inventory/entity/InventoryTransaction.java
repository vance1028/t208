package com.wms.inventory.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "inventory_transaction")
public class InventoryTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "txn_no", nullable = false, unique = true, length = 64)
    private String txnNo;

    @Column(name = "txn_type", nullable = false, length = 32)
    private String txnType;

    @Column(name = "ref_no", length = 64)
    private String refNo;

    @Column(name = "sku_code", nullable = false, length = 64)
    private String skuCode;

    @Column(name = "batch_no", length = 64)
    private String batchNo;

    @Column(name = "location_code", length = 64)
    private String locationCode;

    @Column(name = "qty_change", nullable = false)
    private Integer qtyChange;

    @Column(name = "physical_before")
    private Integer physicalBefore;

    @Column(name = "physical_after")
    private Integer physicalAfter;

    @Column(name = "reserved_before")
    private Integer reservedBefore;

    @Column(name = "reserved_after")
    private Integer reservedAfter;

    @Column(name = "available_before")
    private Integer availableBefore;

    @Column(name = "available_after")
    private Integer availableAfter;

    @Column(name = "operator", length = 64)
    private String operator;

    @Column(name = "remark", length = 500)
    private String remark;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
