package com.wms.inventory.core.enums;

public enum ReservationStatus {
    ACTIVE("预占中"),
    CONFIRMED("已确认拣货"),
    RELEASED("已释放"),
    EXPIRED("已超时");

    private final String label;

    ReservationStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
