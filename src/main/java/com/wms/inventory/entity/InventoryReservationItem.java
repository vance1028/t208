package com.wms.inventory.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "inventory_reservation_item")
public class InventoryReservationItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reservation_id", nullable = false)
    private Long reservationId;

    @Column(name = "sku_code", nullable = false, length = 64)
    private String skuCode;

    @Column(name = "batch_no", nullable = false, length = 64)
    private String batchNo;

    @Column(name = "location_code", nullable = false, length = 64)
    private String locationCode;

    @Column(name = "reserved_qty", nullable = false)
    private Integer reservedQty;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
