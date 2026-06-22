package com.wms.inventory.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "inventory_transfer")
public class InventoryTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transfer_no", nullable = false, unique = true, length = 64)
    private String transferNo;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "COMPLETED";

    @Column(name = "sku_code", nullable = false, length = 64)
    private String skuCode;

    @Column(name = "batch_no", nullable = false, length = 64)
    private String batchNo;

    @Column(name = "from_location", nullable = false, length = 64)
    private String fromLocation;

    @Column(name = "to_location", nullable = false, length = 64)
    private String toLocation;

    @Column(name = "qty", nullable = false)
    private Integer qty;

    @Column(name = "remark", length = 500)
    private String remark;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
