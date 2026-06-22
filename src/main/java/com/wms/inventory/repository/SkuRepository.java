package com.wms.inventory.repository;

import com.wms.inventory.entity.Sku;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SkuRepository extends JpaRepository<Sku, Long> {
    Optional<Sku> findBySkuCode(String skuCode);
    boolean existsBySkuCode(String skuCode);
}
