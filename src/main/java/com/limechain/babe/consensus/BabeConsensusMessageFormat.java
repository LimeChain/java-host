package com.limechain.babe.consensus;

import lombok.Getter;

@Getter
public enum BabeConsensusMessageFormat {
    NEXT_EPOCH_DATA(1), DISABLED_AUTHORITY(2), NEXT_EPOCH_DESCRIPTOR(3);

    private final int format;

    BabeConsensusMessageFormat(int format) {
        this.format = format;
    }

    public static BabeConsensusMessageFormat fromFormat(byte format) {
        for (BabeConsensusMessageFormat messageFormat : values()) {
            if (messageFormat.getFormat() == format) {
                return messageFormat;
            }
        }
        throw new IllegalArgumentException("Unknown babe consensus message format: " + format);
    }
}
