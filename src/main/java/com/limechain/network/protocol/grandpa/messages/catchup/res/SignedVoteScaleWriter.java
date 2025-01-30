package com.limechain.network.protocol.grandpa.messages.catchup.res;

import com.limechain.grandpa.vote.SignedVote;
import com.limechain.network.protocol.grandpa.messages.commit.VoteScaleWriter;
import io.emeraldpay.polkaj.scale.ScaleCodecWriter;
import io.emeraldpay.polkaj.scale.ScaleWriter;

import java.io.IOException;

public class SignedVoteScaleWriter implements ScaleWriter<SignedVote> {

    private static final SignedVoteScaleWriter INSTANCE = new SignedVoteScaleWriter();

    private final VoteScaleWriter voteScaleWriter;

    private SignedVoteScaleWriter() {
        voteScaleWriter = VoteScaleWriter.getInstance();
    }

    public static SignedVoteScaleWriter getInstance() {
        return INSTANCE;
    }

    @Override
    public void write(ScaleCodecWriter writer, SignedVote signedVote) throws IOException {
        voteScaleWriter.write(writer, signedVote.getVote());
        writer.writeByteArray(signedVote.getSignature().getBytes());
        writer.writeUint256(signedVote.getAuthorityPublicKey().getBytes());
    }
}
