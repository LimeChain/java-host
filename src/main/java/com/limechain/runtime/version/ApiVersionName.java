package com.limechain.runtime.version;

import com.limechain.utils.HashUtils;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ApiVersionName {

    AURA_API("AuraApi"),
    BABE_API("BabeApi"),
    GRANDPA_API("GrandpaApi"),
    TRANSACTION_QUEUE_API("TaggedTransactionQueue");

    private final String name;

    public byte[] getHashedName() {
        return HashUtils.hashWithBlake2bToLength(name.getBytes(), ApiVersion.NAME_HASH_LENGTH);
    }
}
