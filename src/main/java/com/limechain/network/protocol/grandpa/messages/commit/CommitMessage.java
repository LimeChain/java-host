package com.limechain.network.protocol.grandpa.messages.commit;

import com.limechain.grandpa.vote.Vote;
import com.limechain.network.protocol.warp.dto.PreCommit;
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
    private PreCommit[] preCommits;
}
