package com.limechain.network.protocol.grandpa.messages.catchup.req;

import com.limechain.exception.scale.WrongMessageTypeException;
import com.limechain.network.protocol.grandpa.messages.GrandpaMessageType;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.scale.ScaleReader;
import io.emeraldpay.polkaj.scale.reader.UInt64Reader;

public class CatchUpReqMessageScaleReader implements ScaleReader<CatchUpReqMessage> {

    private static final CatchUpReqMessageScaleReader INSTANCE = new CatchUpReqMessageScaleReader();

    private final UInt64Reader uint64Reader;

    private CatchUpReqMessageScaleReader() {
        uint64Reader = new UInt64Reader();
    }

    public static CatchUpReqMessageScaleReader getInstance() {
        return INSTANCE;
    }

    @Override
    public CatchUpReqMessage read(ScaleCodecReader reader) {
        int messageType = reader.readByte();
        if (messageType != GrandpaMessageType.CATCH_UP_REQUEST.getType()) {
            throw new WrongMessageTypeException(
                    String.format("Trying to read message of type %d as a catch up request message", messageType));
        }
        CatchUpReqMessage catchUpReqMessage = new CatchUpReqMessage();
        catchUpReqMessage.setRound(uint64Reader.read(reader));
        catchUpReqMessage.setSetId(uint64Reader.read(reader));

        return catchUpReqMessage;
    }

}
