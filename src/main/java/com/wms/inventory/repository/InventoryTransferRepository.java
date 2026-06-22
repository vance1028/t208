package com.wms.inventory.repository;

import com.wms.inventory.entity.InventoryTransfer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InventoryTransferRepository extends JpaRepository<InventoryTransfer, Long> {

    Optional<InventoryTransfer> findByTransferNo(String transferNo);

    Page<InventoryTransfer> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<InventoryTransfer> findBySkuCodeOrderByCreatedAtDesc(String skuCode, Pageable pageable);
}
