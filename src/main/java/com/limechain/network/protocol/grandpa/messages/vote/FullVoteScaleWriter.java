package com.limechain.network.protocol.grandpa.messages.vote;

import com.limechain.network.protocol.grandpa.messages.commit.VoteScaleWriter;
import io.emeraldpay.polkaj.scale.ScaleCodecWriter;
import io.emeraldpay.polkaj.scale.ScaleWriter;
import io.emeraldpay.polkaj.scale.writer.UInt64Writer;

import java.io.IOException;

public class FullVoteScaleWriter implements ScaleWriter<FullVote> {

    private final UInt64Writer uint64Writer;
    private final VoteScaleWriter voteScaleWriter;

    public FullVoteScaleWriter() {
        this.uint64Writer = new UInt64Writer();
        this.voteScaleWriter = new VoteScaleWriter();
    }

    @Override
    public void write(ScaleCodecWriter writer, FullVote fullVote) throws IOException {
        writer.writeByte(fullVote.getStage().getStage());
        voteScaleWriter.write(writer, fullVote.getVote());
        uint64Writer.write(writer, fullVote.getRound());
        uint64Writer.write(writer, fullVote.getSetId());
    }
}
