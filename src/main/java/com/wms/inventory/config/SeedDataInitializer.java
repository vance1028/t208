package com.wms.inventory.config;

import com.wms.inventory.core.enums.ReservationStatus;
import com.wms.inventory.entity.*;
import com.wms.inventory.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeedDataInitializer implements CommandLineRunner {

    private final BatchInventoryRepository batchInventoryRepository;
    private final InventoryReservationRepository reservationRepository;
    private final InventoryReservationItemRepository reservationItemRepository;
    private final SkuRepository skuRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (batchInventoryRepository.count() > 0) {
            log.info("库存数据已存在，跳过种子数据初始化");
            return;
        }
        log.info("开始初始化种子数据...");

        LocalDate today = LocalDate.now();

        createMilkBatches(today);
        createBreadBatches(today);
        createCookieBatches(today);

        createReservations(today);

        log.info("种子数据初始化完成");
    }

    private void createMilkBatches(LocalDate today) {
        saveBatch("SKU001", "MILK-A", "A-01-01",
                today.minusDays(170), today.plusDays(10), 100);
        saveBatch("SKU001", "MILK-B", "A-01-02",
                today.minusDays(120), today.plusDays(60), 150, 20);
        saveBatch("SKU001", "MILK-C", "A-01-03",
                today.minusDays(60), today.plusDays(120), 200);
        saveBatch("SKU001", "MILK-D", "A-02-01",
                today.minusDays(100), today.plusDays(80), 80);
    }

    private void createBreadBatches(LocalDate today) {
        saveBatch("SKU002", "BREAD-A", "B-01-01",
                today.minusDays(5), today.plusDays(2), 50, 10);
        saveBatch("SKU002", "BREAD-B", "B-01-02",
                today.minusDays(2), today.plusDays(5), 120);
        saveBatch("SKU002", "BREAD-C", "B-01-03",
                today.minusDays(1), today.plusDays(6), 90);
    }

    private void createCookieBatches(LocalDate today) {
        saveBatch("SKU003", "COOKIE-A", "C-01-01",
                today.minusDays(200), today.plusDays(165), 300);
        saveBatch("SKU003", "COOKIE-B", "C-01-02",
                today.minusDays(100), today.plusDays(265), 500);
        saveBatch("SKU003", "COOKIE-C", "C-02-01",
                today.minusDays(30), today.plusDays(335), 250, 50);
    }

    private void saveBatch(String skuCode, String batchNo, String location,
                           LocalDate production, LocalDate expiry, int physical) {
        saveBatch(skuCode, batchNo, location, production, expiry, physical, 0);
    }

    private void saveBatch(String skuCode, String batchNo, String location,
                           LocalDate production, LocalDate expiry,
                           int physical, int reserved) {
        BatchInventory bi = new BatchInventory();
        bi.setSkuCode(skuCode);
        bi.setBatchNo(batchNo);
        bi.setLocationCode(location);
        bi.setProductionDate(production);
        bi.setExpiryDate(expiry);
        bi.setInboundTime(production.atStartOfDay().plusHours(8));
        bi.setPhysicalQty(physical);
        bi.setReservedQty(reserved);
        bi.setAvailableQty(physical - reserved);
        batchInventoryRepository.save(bi);
    }

    private void createReservations(LocalDate today) {
        String suffix = DateTimeFormatter.ofPattern("yyyyMMdd").format(today);

        createReservation("RES-SEED-" + suffix + "-01", "ORD-" + suffix + "-001",
                "SKU001", "MILK-B", "A-01-02", 20, "种子数据-预占牛奶20盒");

        createReservation("RES-SEED-" + suffix + "-02", "ORD-" + suffix + "-002",
                "SKU002", "BREAD-A", "B-01-01", 10, "种子数据-预占面包10袋");

        createReservation("RES-SEED-" + suffix + "-03", "ORD-" + suffix + "-003",
                "SKU003", "COOKIE-C", "C-02-01", 50, "种子数据-预占饼干50包");
    }

    private void createReservation(String resNo, String orderNo,
                                   String sku, String batch, String location,
                                   int qty, String remark) {
        InventoryReservation r = new InventoryReservation();
        r.setReservationNo(resNo);
        r.setOrderNo(orderNo);
        r.setStatus(ReservationStatus.ACTIVE.name());
        r.setExpireAt(LocalDateTime.now().plusDays(7));
        r.setRemark(remark);
        r = reservationRepository.save(r);

        InventoryReservationItem item = new InventoryReservationItem();
        item.setReservationId(r.getId());
        item.setSkuCode(sku);
        item.setBatchNo(batch);
        item.setLocationCode(location);
        item.setReservedQty(qty);
        reservationItemRepository.save(item);
    }
}
