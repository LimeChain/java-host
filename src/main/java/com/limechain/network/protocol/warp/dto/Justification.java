package com.limechain.network.protocol.warp.dto;

import com.limechain.grandpa.vote.SignedVote;
import com.limechain.network.protocol.grandpa.messages.catchup.res.CatchUpResMessage;
import com.limechain.network.protocol.grandpa.messages.commit.CommitMessage;
import io.emeraldpay.polkaj.types.Hash256;
import lombok.Getter;
import lombok.Setter;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.stream.Stream;

@Setter
@Getter
public class Justification {

    private BigInteger roundNumber;
    private Hash256 targetHash;
    private BigInteger targetBlock;
    private SignedVote[] signedVotes; // either preCommis or preVotes
    private BlockHeader[] ancestryVotes;

    public static Justification fromCommitMessage(CommitMessage commitMessage) {
        Justification justification = new Justification();
        justification.setRoundNumber(commitMessage.getRoundNumber());
        justification.setTargetHash(commitMessage.getVote().getBlockHash());
        justification.setTargetBlock(commitMessage.getVote().getBlockNumber());
        justification.setSignedVotes(commitMessage.getPreCommits());
        return justification;
    }

    public static Justification fromCatchUpResMessage(CatchUpResMessage catchUpResMessage) {
        SignedVote[] allVotes = Stream.concat(
                Arrays.stream(catchUpResMessage.getPreVotes()),
                Arrays.stream(catchUpResMessage.getPreCommits())
        ).toArray(SignedVote[]::new);

        Justification justification = new Justification();
        justification.setRoundNumber(catchUpResMessage.getRound());
        justification.setTargetHash(catchUpResMessage.getBlockHash());
        justification.setTargetBlock(catchUpResMessage.getBlockNumber());
        justification.setSignedVotes(allVotes);
        return justification;
    }

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
