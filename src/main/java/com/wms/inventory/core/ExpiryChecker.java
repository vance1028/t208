package com.wms.inventory.core;

import com.wms.inventory.core.model.BatchStock;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class ExpiryChecker {

    private ExpiryChecker() {
    }

    public static long daysUntilExpiry(LocalDate expiryDate, LocalDate today) {
        return ChronoUnit.DAYS.between(today, expiryDate);
    }

    public static boolean isExpired(LocalDate expiryDate, LocalDate today) {
        return !expiryDate.isAfter(today);
    }

    public static boolean isNearExpiry(LocalDate expiryDate, LocalDate today, int thresholdDays) {
        if (thresholdDays < 0) {
            throw new IllegalArgumentException("阈值天数不能为负");
        }
        long daysLeft = daysUntilExpiry(expiryDate, today);
        return daysLeft <= thresholdDays;
    }

    public static boolean isNearExpiry(BatchStock batch, LocalDate today, int thresholdDays) {
        return isNearExpiry(batch.getExpiryDate(), today, thresholdDays);
    }

    public static ExpiryStatus getStatus(LocalDate expiryDate, LocalDate today, int thresholdDays) {
        if (isExpired(expiryDate, today)) {
            return ExpiryStatus.EXPIRED;
        }
        if (isNearExpiry(expiryDate, today, thresholdDays)) {
            return ExpiryStatus.NEAR_EXPIRY;
        }
        return ExpiryStatus.NORMAL;
    }

    public enum ExpiryStatus {
        NORMAL("正常"),
        NEAR_EXPIRY("临期"),
        EXPIRED("已过期");

        private final String label;

        ExpiryStatus(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }
}
