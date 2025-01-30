package com.limechain.network.protocol.grandpa.messages.vote;

import com.limechain.grandpa.vote.SubRound;
import com.limechain.grandpa.vote.Vote;
import lombok.Data;

import java.math.BigInteger;

@Data
public class FullVote {
    private SubRound stage;
    private Vote vote;
    private BigInteger round;
    private BigInteger setId;
}
