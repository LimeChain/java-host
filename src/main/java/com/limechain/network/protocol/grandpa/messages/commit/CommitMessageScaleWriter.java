package com.limechain.network.protocol.grandpa.messages.commit;

import com.limechain.network.protocol.grandpa.messages.GrandpaMessageType;
import io.emeraldpay.polkaj.scale.ScaleCodecWriter;
import io.emeraldpay.polkaj.scale.ScaleWriter;
import io.emeraldpay.polkaj.scale.writer.UInt64Writer;

import java.io.IOException;

public class CommitMessageScaleWriter implements ScaleWriter<CommitMessage> {
    private static final CommitMessageScaleWriter INSTANCE = new CommitMessageScaleWriter();
    private final UInt64Writer uint64Writer;
    private final VoteScaleWriter voteScaleWriter;
    private final CompactJustificationScaleWriter compactJustificationScaleWriter;

    public CommitMessageScaleWriter() {
        uint64Writer = new UInt64Writer();
        voteScaleWriter = VoteScaleWriter.getInstance();
        compactJustificationScaleWriter = CompactJustificationScaleWriter.getInstance();
    }

    public static CommitMessageScaleWriter getInstance() {
        return INSTANCE;
    }

    @Override
    public void write(ScaleCodecWriter writer, CommitMessage commitMessage) throws IOException {
        writer.writeByte(GrandpaMessageType.COMMIT.getType());
        uint64Writer.write(writer, commitMessage.getRoundNumber());
        uint64Writer.write(writer, commitMessage.getSetId());
        voteScaleWriter.write(writer, commitMessage.getVote());
        compactJustificationScaleWriter.write(writer, commitMessage.getPreCommits());
    }
}
