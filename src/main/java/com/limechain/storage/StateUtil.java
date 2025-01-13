package com.limechain.storage;

import lombok.experimental.UtilityClass;

import java.math.BigInteger;

@UtilityClass
public class StateUtil {

    /**
     * Prepares a concatenated key by appending all suffixes to the base key.
     * This method builds a key string by appending the provided suffixes sequentially
     * to the base key using a {StringBuilder}.
     *
     * @param key      the base(main) part of the key.
     * @param suffixes additional parts to be appended to the key.
     * @return the concatenated key as a single {String}.
     */
    private String prepareKey(String key, String... suffixes) {
        StringBuilder sb = new StringBuilder(key);
        for (String suffix : suffixes) {
            sb.append(suffix);
        }

        return sb.toString();
    }

    public String generateAuthorityKey(String authorityKey, BigInteger setId) {
        return prepareKey(authorityKey, setId.toString());
    }

    public String generatePrevotesKey(String grandpaPrevotes, BigInteger roundNumber, BigInteger setId) {
        return prepareKey(grandpaPrevotes, roundNumber.toString(), setId.toString());
    }

    public String generatePrecommitsKey(String precommitsKey, BigInteger roundNumber, BigInteger setId) {
        return prepareKey(precommitsKey, roundNumber.toString(), setId.toString());
    }

}
