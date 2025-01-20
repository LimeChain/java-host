package com.limechain.network.protocol.grandpa.messages.commit;

import com.limechain.network.protocol.warp.dto.PreCommit;
import io.emeraldpay.polkaj.scale.ScaleCodecWriter;
import io.emeraldpay.polkaj.scale.ScaleWriter;

import java.io.IOException;

public class CompactJustificationScaleWriter implements ScaleWriter<PreCommit[]> {

    private static final CompactJustificationScaleWriter INSTANCE = new CompactJustificationScaleWriter();

    private final VoteScaleWriter voteScaleWriter;

    private CompactJustificationScaleWriter() {
        voteScaleWriter = VoteScaleWriter.getInstance();
    }

    public static CompactJustificationScaleWriter getInstance() {
        return INSTANCE;
    }

    @Override
    public void write(ScaleCodecWriter writer, PreCommit[] preCommits) throws IOException {
        writer.writeCompact(preCommits.length);

        for (int i = 0; i < preCommits.length; i++) {
            PreCommit preCommit = preCommits[i];
            Vote vote = new Vote();
            vote.setBlockHash(preCommit.getTargetHash());
            vote.setBlockNumber(preCommit.getTargetNumber());
            voteScaleWriter.write(writer, vote);
        }

        writer.writeCompact(preCommits.length);

        for (int i = 0; i < preCommits.length; i++) {
            PreCommit preCommit = preCommits[i];
            writer.writeByteArray(preCommit.getSignature().getBytes());
            writer.writeUint256(preCommit.getAuthorityPublicKey().getBytes());
        }
    }
}
