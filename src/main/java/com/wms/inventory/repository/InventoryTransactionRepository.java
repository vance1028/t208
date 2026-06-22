package com.wms.inventory.repository;

import com.wms.inventory.entity.InventoryTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {

    List<InventoryTransaction> findBySkuCodeOrderByCreatedAtDesc(String skuCode);

    List<InventoryTransaction> findByRefNoOrderByCreatedAtDesc(String refNo);

    @Query("SELECT t FROM InventoryTransaction t WHERE t.skuCode = :skuCode AND t.createdAt BETWEEN :start AND :end ORDER BY t.createdAt DESC")
    List<InventoryTransaction> findBySkuCodeAndTimeRange(
            @Param("skuCode") String skuCode,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    Page<InventoryTransaction> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<InventoryTransaction> findBySkuCodeOrderByCreatedAtDesc(String skuCode, Pageable pageable);
}
