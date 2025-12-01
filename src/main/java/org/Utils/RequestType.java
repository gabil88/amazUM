package org.Utils;

/**
 * Enum representing different types of requests handled by the server.
 */
public enum RequestType {
    Login((short)0),
    Register((short)1),
    AddSale((short)2),
    SalesQuantity((short)3),
    SalesVolume((short)5),
    SalesAveragePrice((short)6),
    SalesMaxPrice((short)7),
    Disconnect((short)8),
    EndDay((short) 4), // Use the next available short value
    Confirmation((short)99); // ou outro valor não usado

    private final short value;

    RequestType(short value) {
        this.value = value;
    }

    public short getValue() {
        return value;
    }

    /**
     * Gets the RequestType enum by its value.
     * @param value The short value of the request type.
     * @return The corresponding RequestType, or null if not found.
     */
    public static RequestType fromValue(short value) {
        for (RequestType type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        return null;
    }
}