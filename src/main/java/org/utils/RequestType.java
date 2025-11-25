package org.utils;

/**
 * Enum representing different types of requests handled by the server.
 */
public enum RequestType {
    Login((short)0),
    Register((short)1),
    AddSale((short)2),
    Disconnect((short)8);

    private final short value;

    RequestType(short value) {
        this.value = value;
    }

    public short getValue() {
        return value;
    }
}