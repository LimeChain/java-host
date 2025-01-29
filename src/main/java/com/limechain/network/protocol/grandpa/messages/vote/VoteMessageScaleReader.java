package com.limechain.network.protocol.grandpa.messages.vote;

import com.limechain.exception.scale.WrongMessageTypeException;
import com.limechain.network.protocol.grandpa.messages.GrandpaMessageType;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.scale.ScaleReader;
import io.emeraldpay.polkaj.scale.reader.UInt64Reader;

public class VoteMessageScaleReader implements ScaleReader<VoteMessage> {

    private static final VoteMessageScaleReader INSTANCE = new VoteMessageScaleReader();

    private final UInt64Reader uint64Reader;
    private final SignedMessageScaleReader signedMessageScaleReader;

    private VoteMessageScaleReader() {
        uint64Reader = new UInt64Reader();
        signedMessageScaleReader = SignedMessageScaleReader.getInstance();
    }

    public static VoteMessageScaleReader getInstance() {
        return INSTANCE;
    }

    @Override
    public VoteMessage read(ScaleCodecReader reader) {
        int messageType = reader.readByte();
        if (messageType != GrandpaMessageType.VOTE.getType()) {
            throw new WrongMessageTypeException(
                    String.format("Trying to read message of type %d as a vote message", messageType));
        }

        VoteMessage voteMessage = new VoteMessage();
        voteMessage.setRound(uint64Reader.read(reader));
        voteMessage.setSetId(uint64Reader.read(reader));
        voteMessage.setMessage(signedMessageScaleReader.read(reader));

        return voteMessage;
    }
}
