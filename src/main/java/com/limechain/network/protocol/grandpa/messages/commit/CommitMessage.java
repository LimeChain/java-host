package com.limechain.network.protocol.grandpa.messages.commit;

import com.limechain.network.protocol.warp.dto.Justification;
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

    public static Justification toJustification(CommitMessage commitMessage) {
        Justification justification = new Justification();
        justification.setRoundNumber(commitMessage.getRoundNumber());
        justification.setTargetHash(commitMessage.getVote().getBlockHash());
        justification.setTargetBlock(commitMessage.getVote().getBlockNumber());
        justification.setPreCommits(commitMessage.getPreCommits());
        return justification;
    }
}
