package com.limechain.storage.crypto;

import com.limechain.runtime.hostapi.dto.Key;
import lombok.Getter;

import java.util.Arrays;

/**
 * The separate key stores are identified by a 4-byte ASCII key type identifier.
 */
@Getter
public enum KeyType {
    /**
     * Key type for the controlling accounts
     */
    CONTROLLING_ACCOUNTS("acco".getBytes(), Key.GENERIC),
    /**
     * Key type for the Babe module
     */
    BABE("babe".getBytes(), Key.SR25519),
    /**
     * Key type for the Grandpa module
     */
    GRANDPA("gran".getBytes(), Key.ED25519),
    /**
     * Key type for the Beefy module
     */
    BEEFY("beef".getBytes(), Key.ECDSA),
    /**
     * Key type for the ImOnline module
     */
    IM_ONLINE("imon".getBytes(), Key.SR25519),
    /**
     * Key type for the AuthorityDiscovery module
     */
    AUTHORITY_DISCOVERY("audi".getBytes(), Key.GENERIC),
    /**
     * Key type for the Parachain Validator Key
     */
    PARACHAIN_VALIDATOR("para".getBytes(), Key.SR25519),
    /**
     * Key type for the Parachain Assignment Key
     */
    PARACHAIN_ASSIGNMENT_KEY("asgn".getBytes(), Key.SR25519);

    public static final int KEY_TYPE_LEN = 4;

    private final byte[] bytes;
    private final Key key;

    KeyType(byte[] bytes, Key key) {
        this.bytes = bytes;
        this.key = key;
    }

    public static KeyType getByBytes(byte[] bytes) {
        for (KeyType keyType : KeyType.values()) {
            if (Arrays.equals(keyType.bytes, bytes)) {
                return keyType;
            }
        }
        return null;
    }
}
