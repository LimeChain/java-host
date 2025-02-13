package com.limechain.network.protocol.grandpa.messages.neighbour;

import com.limechain.network.protocol.grandpa.messages.GrandpaMessageType;
import io.emeraldpay.polkaj.scale.ScaleCodecWriter;
import io.emeraldpay.polkaj.scale.ScaleWriter;
import io.emeraldpay.polkaj.scale.writer.UInt64Writer;

import java.io.IOException;

public class NeighbourMessageScaleWriter implements ScaleWriter<NeighbourMessage> {

    private static final NeighbourMessageScaleWriter INSTANCE = new NeighbourMessageScaleWriter();

    private final UInt64Writer uint64Writer;

    private NeighbourMessageScaleWriter() {
        uint64Writer = new UInt64Writer();
    }

    public static NeighbourMessageScaleWriter getInstance() {
        return INSTANCE;
    }

    @Override
    public void write(ScaleCodecWriter writer, NeighbourMessage neighbourMessage) throws IOException {
        writer.writeByte(GrandpaMessageType.NEIGHBOUR.getType());
        writer.writeByte(neighbourMessage.getVersion());
        uint64Writer.write(writer, neighbourMessage.getRoundNumber());
        uint64Writer.write(writer, neighbourMessage.getSetId());
        writer.writeUint32(neighbourMessage.getLastFinalizedBlock().longValue());
    }
}
