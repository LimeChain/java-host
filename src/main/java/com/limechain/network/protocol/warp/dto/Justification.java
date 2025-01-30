package com.limechain.network.protocol.warp.dto;

import io.emeraldpay.polkaj.types.Hash256;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.java.Log;

import java.math.BigInteger;
import java.util.Arrays;

@Setter
@Getter
@Log
public class Justification {

    private BigInteger round;
    private Hash256 targetHash;
    private BigInteger targetBlock;
    private PreCommit[] preCommits;
    private BlockHeader[] ancestryVotes;

    @Override
    public String toString() {
        return "WarpSyncJustification{" +
                "round=" + round +
                ", targetHash=" + targetHash +
                ", targetBlock=" + targetBlock +
                ", preCommits=" + Arrays.toString(preCommits) +
                ", ancestryVotes=" + Arrays.toString(ancestryVotes) +
                '}';
    }
}
