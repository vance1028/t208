package com.wms.inventory.repository;

import com.wms.inventory.entity.InventoryReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, Long> {

    Optional<InventoryReservation> findByReservationNo(String reservationNo);

    Optional<InventoryReservation> findByOrderNoAndStatus(String orderNo, String status);

    @Query("SELECT r FROM InventoryReservation r WHERE r.status = 'ACTIVE' AND r.expireAt <= :now")
    List<InventoryReservation> findExpiredReservations(@Param("now") LocalDateTime now);

    boolean existsByOrderNoAndStatus(String orderNo, String status);
}
