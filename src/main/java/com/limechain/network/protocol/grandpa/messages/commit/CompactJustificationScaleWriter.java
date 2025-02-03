package com.limechain.network.protocol.grandpa.messages.commit;

import com.limechain.grandpa.vote.SignedVote;
import io.emeraldpay.polkaj.scale.ScaleCodecWriter;
import io.emeraldpay.polkaj.scale.ScaleWriter;

import java.io.IOException;

public class CompactJustificationScaleWriter implements ScaleWriter<SignedVote[]> {

    private static final CompactJustificationScaleWriter INSTANCE = new CompactJustificationScaleWriter();

    private final VoteScaleWriter voteScaleWriter;

    private CompactJustificationScaleWriter() {
        voteScaleWriter = VoteScaleWriter.getInstance();
    }

    public static CompactJustificationScaleWriter getInstance() {
        return INSTANCE;
    }

    @Override
    public void write(ScaleCodecWriter writer, SignedVote[] preCommits) throws IOException {
        writer.writeCompact(preCommits.length);

        for (SignedVote preCommit : preCommits) {
            voteScaleWriter.write(writer, preCommit.getVote());
        }

        writer.writeCompact(preCommits.length);

        for (SignedVote preCommit : preCommits) {
            writer.writeByteArray(preCommit.getSignature().getBytes());
            writer.writeUint256(preCommit.getAuthorityPublicKey().getBytes());
        }
    }
}
