package com.wms.inventory.repository;

import com.wms.inventory.entity.InventoryReservationItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InventoryReservationItemRepository extends JpaRepository<InventoryReservationItem, Long> {

    List<InventoryReservationItem> findByReservationId(Long reservationId);

    void deleteByReservationId(Long reservationId);
}
