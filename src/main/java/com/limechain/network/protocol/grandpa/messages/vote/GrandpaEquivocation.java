package com.limechain.network.protocol.grandpa.messages.vote;

import io.emeraldpay.polkaj.types.Hash256;
import io.emeraldpay.polkaj.types.Hash512;
import lombok.Builder;
import lombok.Getter;

import java.math.BigInteger;

@Getter
@Builder
public class GrandpaEquivocation {
    private BigInteger setId;
    private byte equivocationStage;
    private BigInteger roundNumber;
    private Hash256 authorityPublicKey;
    private Hash256 firstBlockHash;
    private BigInteger firstBlockNumber;
    private Hash512 firstSignature;
    private Hash256 secondBlockHash;
    private BigInteger secondBlockNumber;
    private Hash512 secondSignature;
}
