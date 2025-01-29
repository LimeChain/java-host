package com.limechain.babe.predigest.scale;

import com.limechain.babe.predigest.BabePreDigest;
import com.limechain.babe.predigest.PreDigestType;
import io.emeraldpay.polkaj.scale.ScaleCodecWriter;
import io.emeraldpay.polkaj.scale.ScaleWriter;
import io.emeraldpay.polkaj.scale.writer.UInt64Writer;

import java.io.IOException;

public class PreDigestWriter implements ScaleWriter<BabePreDigest> {

    private static final PreDigestWriter  INSTANCE = new PreDigestWriter();

    private final UInt64Writer uint64Writer;

    private PreDigestWriter() {
        this.uint64Writer = new UInt64Writer();
    }

    public static PreDigestWriter getInstance() {
        return INSTANCE;
    }

    @Override
    public void write(ScaleCodecWriter writer, BabePreDigest preDigest) throws IOException {
        PreDigestType type = preDigest.getType();
        writer.writeByte(type.getValue());
        writer.writeUint32(preDigest.getAuthorityIndex());
        uint64Writer.write(writer, preDigest.getSlotNumber());
        if (!PreDigestType.BABE_SECONDARY_PLAIN.equals(type)) {
            writer.writeByteArray(preDigest.getVrfOutput());
            writer.writeByteArray(preDigest.getVrfProof());
        }
    }
}
