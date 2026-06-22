package com.wms.inventory.core;

import com.wms.inventory.core.model.BatchStock;

public class InventoryLedger {

    private InventoryLedger() {
    }

    public static class LedgerSnapshot {
        private final int physicalBefore;
        private final int physicalAfter;
        private final int reservedBefore;
        private final int reservedAfter;
        private final int availableBefore;
        private final int availableAfter;

        public LedgerSnapshot(int physicalBefore, int physicalAfter,
                              int reservedBefore, int reservedAfter,
                              int availableBefore, int availableAfter) {
            this.physicalBefore = physicalBefore;
            this.physicalAfter = physicalAfter;
            this.reservedBefore = reservedBefore;
            this.reservedAfter = reservedAfter;
            this.availableBefore = availableBefore;
            this.availableAfter = availableAfter;
        }

        public int getPhysicalBefore() { return physicalBefore; }
        public int getPhysicalAfter() { return physicalAfter; }
        public int getReservedBefore() { return reservedBefore; }
        public int getReservedAfter() { return reservedAfter; }
        public int getAvailableBefore() { return availableBefore; }
        public int getAvailableAfter() { return availableAfter; }
    }

    public static LedgerSnapshot inbound(BatchStock stock, int qty) {
        validatePositive(qty);
        int pb = stock.getPhysicalQty();
        int rb = stock.getReservedQty();
        int ab = stock.getAvailableQty();

        stock.setPhysicalQty(pb + qty);
        stock.setAvailableQty(ab + qty);

        validateConsistency(stock);
        return new LedgerSnapshot(pb, stock.getPhysicalQty(), rb, stock.getReservedQty(), ab, stock.getAvailableQty());
    }

    public static LedgerSnapshot outbound(BatchStock stock, int qty) {
        validatePositive(qty);
        if (stock.getAvailableQty() < qty) {
            throw new IllegalStateException(String.format(
                    "可用量不足: 批次[%s]库位[%s]可用%d, 需扣%d",
                    stock.getBatchNo(), stock.getLocationCode(), stock.getAvailableQty(), qty));
        }
        int pb = stock.getPhysicalQty();
        int rb = stock.getReservedQty();
        int ab = stock.getAvailableQty();

        stock.setPhysicalQty(pb - qty);
        stock.setAvailableQty(ab - qty);

        validateNonNegative(stock);
        validateConsistency(stock);
        return new LedgerSnapshot(pb, stock.getPhysicalQty(), rb, stock.getReservedQty(), ab, stock.getAvailableQty());
    }

    public static LedgerSnapshot reserve(BatchStock stock, int qty) {
        validatePositive(qty);
        if (stock.getAvailableQty() < qty) {
            throw new IllegalStateException(String.format(
                    "可用量不足,无法预占: 批次[%s]库位[%s]可用%d, 需预占%d",
                    stock.getBatchNo(), stock.getLocationCode(), stock.getAvailableQty(), qty));
        }
        int pb = stock.getPhysicalQty();
        int rb = stock.getReservedQty();
        int ab = stock.getAvailableQty();

        stock.setReservedQty(rb + qty);
        stock.setAvailableQty(ab - qty);

        validateNonNegative(stock);
        validateConsistency(stock);
        return new LedgerSnapshot(pb, stock.getPhysicalQty(), rb, stock.getReservedQty(), ab, stock.getAvailableQty());
    }

    public static LedgerSnapshot release(BatchStock stock, int qty) {
        validatePositive(qty);
        if (stock.getReservedQty() < qty) {
            throw new IllegalStateException(String.format(
                    "预占量不足,无法释放: 批次[%s]库位[%s]预占%d, 需释放%d",
                    stock.getBatchNo(), stock.getLocationCode(), stock.getReservedQty(), qty));
        }
        int pb = stock.getPhysicalQty();
        int rb = stock.getReservedQty();
        int ab = stock.getAvailableQty();

        stock.setReservedQty(rb - qty);
        stock.setAvailableQty(ab + qty);

        validateNonNegative(stock);
        validateConsistency(stock);
        return new LedgerSnapshot(pb, stock.getPhysicalQty(), rb, stock.getReservedQty(), ab, stock.getAvailableQty());
    }

    public static LedgerSnapshot confirmReservation(BatchStock stock, int qty) {
        validatePositive(qty);
        if (stock.getReservedQty() < qty) {
            throw new IllegalStateException(String.format(
                    "预占量不足,无法确认拣货: 批次[%s]库位[%s]预占%d, 需确认%d",
                    stock.getBatchNo(), stock.getLocationCode(), stock.getReservedQty(), qty));
        }
        int pb = stock.getPhysicalQty();
        int rb = stock.getReservedQty();
        int ab = stock.getAvailableQty();

        stock.setPhysicalQty(pb - qty);
        stock.setReservedQty(rb - qty);

        validateNonNegative(stock);
        validateConsistency(stock);
        return new LedgerSnapshot(pb, stock.getPhysicalQty(), rb, stock.getReservedQty(), ab, stock.getAvailableQty());
    }

    public static void transferOut(BatchStock fromStock, int qty) {
        validatePositive(qty);
        if (fromStock.getPhysicalQty() < qty) {
            throw new IllegalStateException(String.format(
                    "实物量不足,无法调出: 批次[%s]库位[%s]实物%d, 需调出%d",
                    fromStock.getBatchNo(), fromStock.getLocationCode(), fromStock.getPhysicalQty(), qty));
        }
        if (fromStock.getAvailableQty() < qty) {
            throw new IllegalStateException(String.format(
                    "可用量不足,无法调出(有预占未释放): 批次[%s]库位[%s]可用%d, 需调出%d",
                    fromStock.getBatchNo(), fromStock.getLocationCode(), fromStock.getAvailableQty(), qty));
        }
        int pb = fromStock.getPhysicalQty();
        int ab = fromStock.getAvailableQty();
        fromStock.setPhysicalQty(pb - qty);
        fromStock.setAvailableQty(ab - qty);
        validateNonNegative(fromStock);
        validateConsistency(fromStock);
    }

    public static void transferIn(BatchStock toStock, int qty) {
        validatePositive(qty);
        int pb = toStock.getPhysicalQty();
        int ab = toStock.getAvailableQty();
        toStock.setPhysicalQty(pb + qty);
        toStock.setAvailableQty(ab + qty);
        validateConsistency(toStock);
    }

    private static void validatePositive(int qty) {
        if (qty <= 0) {
            throw new IllegalArgumentException("操作数量必须大于0");
        }
    }

    private static void validateNonNegative(BatchStock stock) {
        if (stock.getPhysicalQty() < 0) {
            throw new IllegalStateException("实物量不能为负");
        }
        if (stock.getReservedQty() < 0) {
            throw new IllegalStateException("预占量不能为负");
        }
        if (stock.getAvailableQty() < 0) {
            throw new IllegalStateException("可用量不能为负");
        }
    }

    private static void validateConsistency(BatchStock stock) {
        int expectedAvailable = stock.getPhysicalQty() - stock.getReservedQty();
        if (stock.getAvailableQty() != expectedAvailable) {
            throw new IllegalStateException(String.format(
                    "账目不一致: 实物%d - 预占%d = 可用%d, 但实际可用为%d",
                    stock.getPhysicalQty(), stock.getReservedQty(), expectedAvailable, stock.getAvailableQty()));
        }
    }
}
