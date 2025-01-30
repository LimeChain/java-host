package com.limechain.network.protocol.grandpa.messages.catchup.res;

import com.limechain.exception.scale.WrongMessageTypeException;
import com.limechain.grandpa.vote.SignedVote;
import com.limechain.network.protocol.grandpa.messages.GrandpaMessageType;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.scale.ScaleReader;
import io.emeraldpay.polkaj.scale.reader.ListReader;
import io.emeraldpay.polkaj.scale.reader.UInt32Reader;
import io.emeraldpay.polkaj.scale.reader.UInt64Reader;
import io.emeraldpay.polkaj.types.Hash256;

import java.math.BigInteger;

public class CatchUpResMessageScaleReader implements ScaleReader<CatchUpResMessage> {

    private static final CatchUpResMessageScaleReader INSTANCE = new CatchUpResMessageScaleReader();

    private final UInt32Reader uint32Reader;
    private final UInt64Reader uint64Reader;
    private final ListReader<SignedVote> signedVoteListReader;

    private CatchUpResMessageScaleReader() {
        uint32Reader = new UInt32Reader();
        uint64Reader = new UInt64Reader();
        signedVoteListReader = new ListReader<>(SignedVoteScaleReader.getInstance());
    }

    public static CatchUpResMessageScaleReader getInstance() {
        return INSTANCE;
    }

    @Override
    public CatchUpResMessage read(ScaleCodecReader reader) {
        int messageType = reader.readByte();
        if (messageType != GrandpaMessageType.CATCH_UP_RESPONSE.getType()) {
            throw new WrongMessageTypeException(
                    String.format("Trying to read message of type %d as a catch up response message", messageType));
        }

        CatchUpResMessage catchUpResMessage = new CatchUpResMessage();
        catchUpResMessage.setSetId(uint64Reader.read(reader));
        catchUpResMessage.setRound(uint64Reader.read(reader));
        catchUpResMessage.setPreVotes(signedVoteListReader
                .read(reader).toArray(SignedVote[]::new));
        catchUpResMessage.setPreCommits(signedVoteListReader
                .read(reader).toArray(SignedVote[]::new));
        catchUpResMessage.setBlockHash(new Hash256(reader.readUint256()));
        catchUpResMessage.setBlockNumber(BigInteger.valueOf(uint32Reader.read(reader)));

        return catchUpResMessage;
    }
}
