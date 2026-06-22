package com.wms.inventory.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "batch_inventory", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"sku_code", "batch_no", "location_code"})
})
public class BatchInventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sku_code", nullable = false, length = 64)
    private String skuCode;

    @Column(name = "batch_no", nullable = false, length = 64)
    private String batchNo;

    @Column(name = "location_code", nullable = false, length = 64)
    private String locationCode;

    @Column(name = "production_date", nullable = false)
    private LocalDate productionDate;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @Column(name = "inbound_time", nullable = false)
    private LocalDateTime inboundTime;

    @Column(name = "physical_qty", nullable = false)
    private Integer physicalQty = 0;

    @Column(name = "reserved_qty", nullable = false)
    private Integer reservedQty = 0;

    @Column(name = "available_qty", nullable = false)
    private Integer availableQty = 0;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
