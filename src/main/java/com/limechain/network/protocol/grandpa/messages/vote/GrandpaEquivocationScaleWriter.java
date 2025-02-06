package com.limechain.network.protocol.grandpa.messages.vote;

import io.emeraldpay.polkaj.scale.ScaleCodecWriter;
import io.emeraldpay.polkaj.scale.ScaleWriter;
import io.emeraldpay.polkaj.scale.writer.UInt64Writer;

import java.io.IOException;

public class GrandpaEquivocationScaleWriter implements ScaleWriter<GrandpaEquivocation> {

    private static final GrandpaEquivocationScaleWriter INSTANCE = new GrandpaEquivocationScaleWriter();

    private final UInt64Writer uint64Writer;

    private GrandpaEquivocationScaleWriter() {
        this.uint64Writer = new UInt64Writer();
    }

    public static GrandpaEquivocationScaleWriter getInstance() {
        return INSTANCE;
    }

    @Override
    public void write(ScaleCodecWriter writer, GrandpaEquivocation grandpaEquivocation) throws IOException {
        uint64Writer.write(writer, grandpaEquivocation.getSetId());
        writer.writeByte(grandpaEquivocation.getEquivocationStage());
        uint64Writer.write(writer, grandpaEquivocation.getRoundNumber());
        writer.writeByteArray(grandpaEquivocation.getAuthorityPublicKey().getBytes());
        uint64Writer.write(writer, grandpaEquivocation.getFirstBlockNumber());
        writer.writeByteArray(grandpaEquivocation.getFirstBlockHash().getBytes());
        writer.writeByteArray(grandpaEquivocation.getFirstSignature().getBytes());
        uint64Writer.write(writer, grandpaEquivocation.getSecondBlockNumber());
        writer.writeByteArray(grandpaEquivocation.getSecondBlockHash().getBytes());
        writer.writeByteArray(grandpaEquivocation.getSecondSignature().getBytes());
    }
}
