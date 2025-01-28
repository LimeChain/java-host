package com.limechain.network.protocol.warp.dto;

import io.emeraldpay.polkaj.types.Hash256;
import lombok.Getter;
import lombok.Setter;

import java.math.BigInteger;
import java.util.Arrays;

@Setter
@Getter
public class Justification {
    private BigInteger roundNumber;
    private Hash256 targetHash;
    private BigInteger targetBlock;
    private PreCommit[] preCommits;
    private BlockHeader[] ancestryVotes;

    @Override
    public String toString() {
        return "Justification{" +
                "roundNumber=" + roundNumber +
                ", targetHash=" + targetHash +
                ", targetBlock=" + targetBlock +
                ", preCommits=" + Arrays.toString(preCommits) +
                ", ancestryVotes=" + Arrays.toString(ancestryVotes) +
                '}';
    }
}
