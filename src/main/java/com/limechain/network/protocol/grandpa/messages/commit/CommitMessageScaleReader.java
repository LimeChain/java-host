package com.limechain.network.protocol.grandpa.messages.commit;

import com.limechain.exception.scale.WrongMessageTypeException;
import com.limechain.network.protocol.grandpa.messages.GrandpaMessageType;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.scale.ScaleReader;
import io.emeraldpay.polkaj.scale.reader.UInt64Reader;

public class CommitMessageScaleReader implements ScaleReader<CommitMessage> {

    private static final CommitMessageScaleReader INSTANCE = new CommitMessageScaleReader();

    private final UInt64Reader uint64Reader;
    private final VoteScaleReader voteScaleReader;
    private final CompactJustificationScaleReader compactJustificationScaleReader;

    private CommitMessageScaleReader() {
        uint64Reader = new UInt64Reader();
        voteScaleReader = VoteScaleReader.getInstance();
        compactJustificationScaleReader = CompactJustificationScaleReader.getInstance();
    }

    public static CommitMessageScaleReader getInstance() {
        return INSTANCE;
    }

    @Override
    public CommitMessage read(ScaleCodecReader reader) {
        int messageType = reader.readByte();
        if (messageType != GrandpaMessageType.COMMIT.getType()) {
            throw new WrongMessageTypeException(
                    String.format("Trying to read message of type %d as a commit message", messageType));
        }
        CommitMessage commitMessage = new CommitMessage();
        commitMessage.setRoundNumber(uint64Reader.read(reader));
        commitMessage.setSetId(uint64Reader.read(reader));
        commitMessage.setVote(voteScaleReader.read(reader));
        commitMessage.setPreCommits(compactJustificationScaleReader.read(reader));
        return commitMessage;
    }
}
