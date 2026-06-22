package com.wms.inventory.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class ExpiryCheckerTest {

    private final LocalDate today = LocalDate.of(2026, 6, 22);

    @Test
    @DisplayName("daysUntilExpiry: 到期前返回正数，到期日返回0，过期返回负数")
    void daysUntilExpiryCalculation() {
        assertEquals(10, ExpiryChecker.daysUntilExpiry(today.plusDays(10), today));
        assertEquals(0, ExpiryChecker.daysUntilExpiry(today, today));
        assertEquals(-5, ExpiryChecker.daysUntilExpiry(today.minusDays(5), today));
    }

    @Test
    @DisplayName("isExpired: 到期日及之前视为已过期")
    void expiredDetection() {
        assertFalse(ExpiryChecker.isExpired(today.plusDays(1), today));
        assertTrue(ExpiryChecker.isExpired(today, today));
        assertTrue(ExpiryChecker.isExpired(today.minusDays(1), today));
    }

    @Test
    @DisplayName("isNearExpiry: 按阈值正确筛选临期批次")
    void nearExpiryByThreshold() {
        int threshold = 30;
        assertFalse(ExpiryChecker.isNearExpiry(today.plusDays(31), today, threshold));
        assertTrue(ExpiryChecker.isNearExpiry(today.plusDays(30), today, threshold));
        assertTrue(ExpiryChecker.isNearExpiry(today.plusDays(15), today, threshold));
        assertTrue(ExpiryChecker.isNearExpiry(today, today, threshold));
        assertTrue(ExpiryChecker.isNearExpiry(today.minusDays(5), today, threshold));
    }

    @Test
    @DisplayName("getStatus: 正确返回三态（正常/临期/过期）")
    void statusTriState() {
        int threshold = 30;
        assertEquals(ExpiryChecker.ExpiryStatus.NORMAL,
                ExpiryChecker.getStatus(today.plusDays(60), today, threshold));
        assertEquals(ExpiryChecker.ExpiryStatus.NEAR_EXPIRY,
                ExpiryChecker.getStatus(today.plusDays(15), today, threshold));
        assertEquals(ExpiryChecker.ExpiryStatus.NEAR_EXPIRY,
                ExpiryChecker.getStatus(today.plusDays(30), today, threshold));
        assertEquals(ExpiryChecker.ExpiryStatus.EXPIRED,
                ExpiryChecker.getStatus(today, today, threshold));
        assertEquals(ExpiryChecker.ExpiryStatus.EXPIRED,
                ExpiryChecker.getStatus(today.minusDays(1), today, threshold));
    }

    @Test
    @DisplayName("阈值为负时抛异常")
    void negativeThresholdThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> ExpiryChecker.isNearExpiry(today.plusDays(10), today, -1));
    }
}
