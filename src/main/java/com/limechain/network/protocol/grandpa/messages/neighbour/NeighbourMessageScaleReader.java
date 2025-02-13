package com.limechain.network.protocol.grandpa.messages.neighbour;

import com.limechain.exception.scale.WrongMessageTypeException;
import com.limechain.network.protocol.grandpa.messages.GrandpaMessageType;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.scale.ScaleReader;
import io.emeraldpay.polkaj.scale.reader.UInt64Reader;

import java.math.BigInteger;

public class NeighbourMessageScaleReader implements ScaleReader<NeighbourMessage> {

    private static final NeighbourMessageScaleReader INSTANCE = new NeighbourMessageScaleReader();

    private final UInt64Reader uint64Reader;

    private NeighbourMessageScaleReader() {
        uint64Reader = new UInt64Reader();
    }

    public static NeighbourMessageScaleReader getInstance() {
        return INSTANCE;
    }

    @Override
    public NeighbourMessage read(ScaleCodecReader reader) {
        int messageType = reader.readByte();
        if (messageType != GrandpaMessageType.NEIGHBOUR.getType()) {
            throw new WrongMessageTypeException(
                    String.format("Trying to read message of type %d as a neighbour message", messageType));
        }
        NeighbourMessage neighbourMessage = new NeighbourMessage();
        neighbourMessage.setVersion(reader.readByte());
        neighbourMessage.setRoundNumber(uint64Reader.read(reader));
        neighbourMessage.setSetId(uint64Reader.read(reader));
        neighbourMessage.setLastFinalizedBlock(BigInteger.valueOf(reader.readUint32()));
        return neighbourMessage;
    }
}
