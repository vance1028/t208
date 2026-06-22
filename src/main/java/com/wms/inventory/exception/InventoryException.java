package com.wms.inventory.exception;

public class InventoryException extends RuntimeException {

    private final int errorCode;

    public InventoryException(String message) {
        super(message);
        this.errorCode = 400;
    }

    public InventoryException(int errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public InventoryException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = 400;
    }

    public int getErrorCode() {
        return errorCode;
    }
}
