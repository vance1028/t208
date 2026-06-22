package com.wms.inventory.repository;

import com.wms.inventory.entity.BatchInventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BatchInventoryRepository extends JpaRepository<BatchInventory, Long> {

    Optional<BatchInventory> findBySkuCodeAndBatchNoAndLocationCode(String skuCode, String batchNo, String locationCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM BatchInventory b WHERE b.skuCode = :skuCode AND b.batchNo = :batchNo AND b.locationCode = :locationCode")
    Optional<BatchInventory> findBySkuCodeAndBatchNoAndLocationCodeForUpdate(
            @Param("skuCode") String skuCode,
            @Param("batchNo") String batchNo,
            @Param("locationCode") String locationCode);

    @Query("SELECT b FROM BatchInventory b WHERE b.skuCode = :skuCode AND b.availableQty > 0 ORDER BY b.expiryDate ASC, b.productionDate ASC, b.id ASC")
    List<BatchInventory> findAvailableBatchesOrderByExpiry(@Param("skuCode") String skuCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM BatchInventory b WHERE b.skuCode = :skuCode AND b.availableQty > 0 ORDER BY b.expiryDate ASC, b.productionDate ASC, b.id ASC")
    List<BatchInventory> findAvailableBatchesOrderByExpiryForUpdate(@Param("skuCode") String skuCode);

    List<BatchInventory> findBySkuCode(String skuCode);

    List<BatchInventory> findByLocationCode(String locationCode);

    @Query("SELECT b FROM BatchInventory b WHERE b.expiryDate <= :thresholdDate AND (b.physicalQty > 0 OR b.reservedQty > 0) ORDER BY b.expiryDate ASC")
    List<BatchInventory> findNearExpiryBatches(@Param("thresholdDate") LocalDate thresholdDate);

    @Query("SELECT b FROM BatchInventory b WHERE b.skuCode = :skuCode AND b.expiryDate <= :thresholdDate AND (b.physicalQty > 0 OR b.reservedQty > 0) ORDER BY b.expiryDate ASC")
    List<BatchInventory> findNearExpiryBatchesBySku(@Param("skuCode") String skuCode, @Param("thresholdDate") LocalDate thresholdDate);

    @Query("SELECT COALESCE(SUM(b.physicalQty), 0) FROM BatchInventory b WHERE b.skuCode = :skuCode")
    int sumPhysicalQtyBySkuCode(@Param("skuCode") String skuCode);

    @Query("SELECT COALESCE(SUM(b.reservedQty), 0) FROM BatchInventory b WHERE b.skuCode = :skuCode")
    int sumReservedQtyBySkuCode(@Param("skuCode") String skuCode);

    @Query("SELECT COALESCE(SUM(b.availableQty), 0) FROM BatchInventory b WHERE b.skuCode = :skuCode")
    int sumAvailableQtyBySkuCode(@Param("skuCode") String skuCode);
}
