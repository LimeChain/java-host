package com.limechain.network.protocol.grandpa.messages.catchup.res;

import com.limechain.grandpa.vote.SignedVote;
import com.limechain.network.protocol.grandpa.messages.GrandpaMessageType;
import io.emeraldpay.polkaj.scale.ScaleCodecWriter;
import io.emeraldpay.polkaj.scale.ScaleWriter;
import io.emeraldpay.polkaj.scale.writer.ListWriter;
import io.emeraldpay.polkaj.scale.writer.UInt64Writer;

import java.io.IOException;
import java.util.Arrays;

public class CatchUpResMessageScaleWriter implements ScaleWriter<CatchUpResMessage> {
    private static final CatchUpResMessageScaleWriter INSTANCE = new CatchUpResMessageScaleWriter();
    
    private final UInt64Writer uint64Writer;
    private final ListWriter<SignedVote> signedVoteListWriter;

    private CatchUpResMessageScaleWriter() {
        uint64Writer = new UInt64Writer();
        signedVoteListWriter = new ListWriter<>(SignedVoteScaleWriter.getInstance());
    }

    public static CatchUpResMessageScaleWriter getInstance() {
        return INSTANCE;
    }

    @Override
    public void write(ScaleCodecWriter writer, CatchUpResMessage catchUpResMessage) throws IOException {
        writer.writeByte(GrandpaMessageType.CATCH_UP_RESPONSE.getType());
        uint64Writer.write(writer, catchUpResMessage.getSetId());
        uint64Writer.write(writer, catchUpResMessage.getRound());
        signedVoteListWriter.write(writer, Arrays.asList(catchUpResMessage.getPreVotes()));
        signedVoteListWriter.write(writer, Arrays.asList(catchUpResMessage.getPreCommits()));
        writer.writeUint256(catchUpResMessage.getBlockHash().getBytes());
        writer.writeUint32(catchUpResMessage.getBlockNumber().longValue());
    }
}
