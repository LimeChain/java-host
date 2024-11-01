package com.limechain.babe.predigest;

import lombok.Getter;

@Getter
public enum PreDigestType {
    BABE_PRIMARY(1),
    BABE_SECONDARY_PLAIN(2),
    BABE_SECONDARY_VRF(3);

    private final byte value;

    PreDigestType(int value) {
        this.value = (byte) value;
    }

    public static PreDigestType getByValue(byte value) {
        for (PreDigestType type : PreDigestType.values()) {
            if (type.value == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown PreDigestType value: " + value);
    }
}
