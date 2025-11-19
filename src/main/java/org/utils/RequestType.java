package org.utils;

public enum RequestType {
    Login((short)0),
    Register((short)1),
    NewOrder((short)2),
    ListOrders((short)3),
    CancelOrder((short)4),
    NewDelivery((short)5),
    ListDeliveries((short)6),
    UpdateStatus((short)7),
    Success((short)8),
    Error((short)9);

    private final short value;

    RequestType(short value) {
        this.value = value;
    }

    public short getValue() {
        return value;
    }
}