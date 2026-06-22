package com.wms.inventory.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "sku")
public class Sku {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sku_code", nullable = false, unique = true, length = 64)
    private String skuCode;

    @Column(name = "sku_name", nullable = false, length = 255)
    private String skuName;

    @Column(name = "unit", nullable = false, length = 32)
    private String unit = "件";

    @Column(name = "shelf_life_days")
    private Integer shelfLifeDays;

    @Column(name = "description", length = 500)
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
