package com.limechain.babe.predigest.scale;

import com.limechain.babe.predigest.PreDigestType;
import com.limechain.babe.predigest.BabePreDigest;
import io.emeraldpay.polkaj.scale.ScaleCodecWriter;
import io.emeraldpay.polkaj.scale.ScaleWriter;

import java.io.IOException;


public class PreDigestWriter implements ScaleWriter<BabePreDigest> {

    @Override
    public void write(ScaleCodecWriter writer, BabePreDigest preDigest) throws IOException {
        PreDigestType type = preDigest.getType();
        writer.writeByte(type.getValue());
        writer.writeUint32(preDigest.getAuthorityIndex());
        writer.writeUint128(preDigest.getSlotNumber());
        if (!PreDigestType.BABE_SECONDARY_PLAIN.equals(type)) {
            writer.writeByteArray(preDigest.getVrfOutput());
            writer.writeByteArray(preDigest.getVrfProof());
        }
    }
}
