package com.limechain.network.protocol.grandpa.messages.vote;

import com.limechain.network.protocol.grandpa.messages.commit.Vote;
import lombok.Data;

import java.math.BigInteger;

@Data
public class FullVote {
    private Subround stage;
    private Vote vote;
    private BigInteger round;
    private BigInteger setId;
}
