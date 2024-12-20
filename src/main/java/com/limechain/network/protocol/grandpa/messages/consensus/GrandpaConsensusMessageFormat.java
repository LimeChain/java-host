package com.limechain.network.protocol.grandpa.messages.consensus;

import lombok.Getter;

@Getter
public enum GrandpaConsensusMessageFormat {
    GRANDPA_SCHEDULED_CHANGE(1), GRANDPA_FORCED_CHANGE(2), GRANDPA_ON_DISABLED(3), GRANDPA_PAUSE(4), GRANDPA_RESUME(5);

    private final int format;

    GrandpaConsensusMessageFormat(int format) {
        this.format = format;
    }

    public static GrandpaConsensusMessageFormat fromFormat(byte format) {
        for (GrandpaConsensusMessageFormat messageFormat : values()) {
            if (messageFormat.getFormat() == format) {
                return messageFormat;
            }
        }
        throw new IllegalArgumentException("Unknown grandpa consensus message format: " + format);
    }
}
