package com.limechain.network.protocol.warp.dto;

import io.emeraldpay.polkaj.types.Hash256;
import io.emeraldpay.polkaj.types.Hash512;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PreCommit {
    private Hash256 targetHash;
    private BigInteger targetNumber;
    private Hash512 signature;
    private Hash256 authorityPublicKey;

    @Override
    public String toString() {
        return "PreCommit{" +
                "targetHash=" + targetHash +
                ", targetNumber=" + targetNumber +
                ", signature=" + signature +
                ", authorityPublicKey=" + authorityPublicKey +
                '}';
    }
}
