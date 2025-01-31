package com.limechain.network.protocol.grandpa.messages.commit;

import com.limechain.grandpa.vote.SignedVote;
import com.limechain.grandpa.vote.Vote;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CommitMessage {
    private BigInteger roundNumber;
    private BigInteger setId;
    private Vote vote;
    private SignedVote[] preCommits;
}
