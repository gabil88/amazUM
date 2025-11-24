package org.utils;

/**
 * Enum representing different types of requests handled by the server.
 */
public enum RequestType {
    Login((short)0),
    Register((short)1),
    NewOrder((short)2),
    ListOrders((short)3),
    CancelOrder((short)4),
    NewDelivery((short)5),
    ListDeliveries((short)6),
    UpdateStatus((short)7),
    Disconnect((short)8);

    private final short value;

    RequestType(short value) {
        this.value = value;
    }

    public short getValue() {
        return value;
    }
}