package com.wms.inventory.core.enums;

public enum TxnType {
    INBOUND("入库"),
    OUTBOUND("出库"),
    RESERVE("预占"),
    RELEASE("释放预占"),
    CONFIRM("确认拣货"),
    TRANSFER_IN("调拨入"),
    TRANSFER_OUT("调拨出");

    private final String label;

    TxnType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
