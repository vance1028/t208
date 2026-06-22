package com.wms.inventory.core;

import com.wms.inventory.core.model.BatchStock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class InventoryLedgerTest {

    private BatchStock stock;

    @BeforeEach
    void setUp() {
        stock = BatchStock.builder()
                .id(1L)
                .skuCode("TEST")
                .batchNo("B1")
                .locationCode("L1")
                .productionDate(LocalDate.now().minusDays(10))
                .expiryDate(LocalDate.now().plusDays(100))
                .physicalQty(100)
                .reservedQty(0)
                .availableQty(100)
                .build();
    }

    @Test
    @DisplayName("入库: 实物+可用同时增加，预占不变，账目守恒")
    void inboundAddsPhysicalAndAvailable() {
        InventoryLedger.LedgerSnapshot snap = InventoryLedger.inbound(stock, 50);

        assertEquals(100, snap.getPhysicalBefore());
        assertEquals(150, snap.getPhysicalAfter());
        assertEquals(0, snap.getReservedBefore());
        assertEquals(0, snap.getReservedAfter());
        assertEquals(100, snap.getAvailableBefore());
        assertEquals(150, snap.getAvailableAfter());

        assertEquals(150, stock.getPhysicalQty());
        assertEquals(0, stock.getReservedQty());
        assertEquals(150, stock.getAvailableQty());
        assertEquals(stock.getPhysicalQty() - stock.getReservedQty(), stock.getAvailableQty());
    }

    @Test
    @DisplayName("出库: 实物+可用同时减少，预占不变")
    void outboundReducesPhysicalAndAvailable() {
        InventoryLedger.LedgerSnapshot snap = InventoryLedger.outbound(stock, 30);

        assertEquals(70, stock.getPhysicalQty());
        assertEquals(0, stock.getReservedQty());
        assertEquals(70, stock.getAvailableQty());
        assertEquals(30, snap.getPhysicalBefore() - snap.getPhysicalAfter());
    }

    @Test
    @DisplayName("出库超出可用量抛异常")
    void outboundExceedAvailableThrows() {
        assertThrows(IllegalStateException.class, () -> InventoryLedger.outbound(stock, 101));
        assertEquals(100, stock.getPhysicalQty());
        assertEquals(100, stock.getAvailableQty());
    }

    @Test
    @DisplayName("预占: 预占增加，可用减少，实物不变")
    void reserveIncreasesReservedDecreasesAvailable() {
        InventoryLedger.LedgerSnapshot snap = InventoryLedger.reserve(stock, 40);

        assertEquals(100, stock.getPhysicalQty());
        assertEquals(40, stock.getReservedQty());
        assertEquals(60, stock.getAvailableQty());
        assertEquals(100 - 40, stock.getAvailableQty());
        assertEquals(40, snap.getReservedAfter() - snap.getReservedBefore());
    }

    @Test
    @DisplayName("预占超出可用量抛异常")
    void reserveExceedAvailableThrows() {
        assertThrows(IllegalStateException.class, () -> InventoryLedger.reserve(stock, 101));
        assertEquals(100, stock.getPhysicalQty());
        assertEquals(0, stock.getReservedQty());
        assertEquals(100, stock.getAvailableQty());
    }

    @Test
    @DisplayName("释放预占: 预占减少，可用增加，实物不变")
    void releaseDecreasesReservedIncreasesAvailable() {
        InventoryLedger.reserve(stock, 40);
        assertEquals(60, stock.getAvailableQty());

        InventoryLedger.LedgerSnapshot snap = InventoryLedger.release(stock, 20);

        assertEquals(100, stock.getPhysicalQty());
        assertEquals(20, stock.getReservedQty());
        assertEquals(80, stock.getAvailableQty());
        assertEquals(20, snap.getReservedBefore() - snap.getReservedAfter());
    }

    @Test
    @DisplayName("释放量超出预占量抛异常")
    void releaseExceedReservedThrows() {
        InventoryLedger.reserve(stock, 40);
        assertThrows(IllegalStateException.class, () -> InventoryLedger.release(stock, 41));
        assertEquals(40, stock.getReservedQty());
        assertEquals(60, stock.getAvailableQty());
    }

    @Test
    @DisplayName("确认拣货: 实物和预占同时减少，可用不变")
    void confirmReducesPhysicalAndReserved() {
        InventoryLedger.reserve(stock, 40);
        assertEquals(100, stock.getPhysicalQty());
        assertEquals(40, stock.getReservedQty());
        assertEquals(60, stock.getAvailableQty());

        InventoryLedger.LedgerSnapshot snap = InventoryLedger.confirmReservation(stock, 30);

        assertEquals(70, stock.getPhysicalQty());
        assertEquals(10, stock.getReservedQty());
        assertEquals(60, stock.getAvailableQty());
        assertEquals(stock.getPhysicalQty() - stock.getReservedQty(), stock.getAvailableQty());
    }

    @Test
    @DisplayName("确认拣货量超出预占量抛异常")
    void confirmExceedReservedThrows() {
        InventoryLedger.reserve(stock, 40);
        assertThrows(IllegalStateException.class, () -> InventoryLedger.confirmReservation(stock, 41));
        assertEquals(100, stock.getPhysicalQty());
        assertEquals(40, stock.getReservedQty());
        assertEquals(60, stock.getAvailableQty());
    }

    @Test
    @DisplayName("调拨出入数量守恒")
    void transferConservesQuantity() {
        BatchStock from = BatchStock.builder()
                .physicalQty(100).reservedQty(0).availableQty(100).build();
        BatchStock to = BatchStock.builder()
                .physicalQty(50).reservedQty(0).availableQty(50).build();

        int totalBefore = from.getPhysicalQty() + to.getPhysicalQty();
        InventoryLedger.transferOut(from, 30);
        InventoryLedger.transferIn(to, 30);
        int totalAfter = from.getPhysicalQty() + to.getPhysicalQty();

        assertEquals(70, from.getPhysicalQty());
        assertEquals(70, from.getAvailableQty());
        assertEquals(80, to.getPhysicalQty());
        assertEquals(80, to.getAvailableQty());
        assertEquals(totalBefore, totalAfter);
    }

    @Test
    @DisplayName("调拨出超出可用量抛异常（有预占时）")
    void transferOutWithReservationThrows() {
        stock.setReservedQty(60);
        stock.setAvailableQty(40);
        assertThrows(IllegalStateException.class, () -> InventoryLedger.transferOut(stock, 50));
        assertEquals(100, stock.getPhysicalQty());
    }

    @Test
    @DisplayName("账目不一致检测: 手动破坏数据后操作被拦截")
    void detectsInconsistentState() {
        stock.setAvailableQty(999);
        assertThrows(IllegalStateException.class, () -> InventoryLedger.inbound(stock, 10));
    }

    @Test
    @DisplayName("操作数量为0或负数抛异常")
    void invalidQuantityThrows() {
        assertThrows(IllegalArgumentException.class, () -> InventoryLedger.inbound(stock, 0));
        assertThrows(IllegalArgumentException.class, () -> InventoryLedger.outbound(stock, -5));
        assertThrows(IllegalArgumentException.class, () -> InventoryLedger.reserve(stock, 0));
    }

    @Test
    @DisplayName("多步操作后账目始终守恒")
    void multiStepOperationsStayConsistent() {
        InventoryLedger.inbound(stock, 50);
        InventoryLedger.reserve(stock, 40);
        InventoryLedger.release(stock, 10);
        InventoryLedger.reserve(stock, 20);
        InventoryLedger.confirmReservation(stock, 30);
        InventoryLedger.outbound(stock, 40);
        InventoryLedger.inbound(stock, 100);

        assertTrue(stock.getPhysicalQty() >= 0);
        assertTrue(stock.getReservedQty() >= 0);
        assertTrue(stock.getAvailableQty() >= 0);
        assertEquals(stock.getPhysicalQty() - stock.getReservedQty(), stock.getAvailableQty());
    }
}
