package com.limechain.rpc.pubsub;

import lombok.Getter;

/**
 * Holds all possible subscription topics
 */
@Getter
public enum Topic {
    UNSTABLE_FOLLOW("unstable_follow"),
    UNSTABLE_TRANSACTION_WATCH("transaction_watch"),
    CHAIN_ALL_HEAD("chain_allHead"),
    CHAIN_NEW_HEAD("chain_newHead"),
    CHAIN_FINALIZED_HEAD("chain_finalizedHead"),
    STATE_RUNTIME_VERSION("state_runtimeVersion"),
    STATE_STORAGE("state_storage"),
    AUTHOR_EXTRINSIC_UPDATE("author_extrinsicUpdate");

    private final String value;

    Topic(String value) {
        this.value = value;
    }

    /**
     * Tries to map string parameter to an enum value
     *
     * @param topic name of the enum value to map
     * @return enum value or null if mapping is unsuccessful
     */
    public static Topic fromString(String topic) {
        for (Topic type : values()) {
            if (type.getValue().equals(topic)) {
                return type;
            }
        }
        return null;
    }
}
