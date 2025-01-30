package com.limechain.network.protocol.warp.dto;

import com.limechain.network.protocol.grandpa.messages.catchup.res.SignedVote;
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
    private SignedVote[] signedVotes; // either preCommis or preVotes
    private BlockHeader[] ancestryVotes;

    @Override
    public String toString() {
        return "Justification{" +
                "roundNumber=" + roundNumber +
                ", targetHash=" + targetHash +
                ", targetBlock=" + targetBlock +
                ", signedVotes=" + Arrays.toString(signedVotes) +
                ", ancestryVotes=" + Arrays.toString(ancestryVotes) +
                '}';
    }
}
