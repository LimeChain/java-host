package com.limechain.babe.predigest.scale;

import com.limechain.babe.predigest.BabePreDigest;
import com.limechain.babe.predigest.PreDigestType;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.scale.ScaleReader;
import io.emeraldpay.polkaj.scale.reader.UInt64Reader;

import static io.emeraldpay.polkaj.schnorrkel.VrfOutputAndProof.OUTPUT_BYTE_LEN;
import static io.emeraldpay.polkaj.schnorrkel.VrfOutputAndProof.PROOF_BYTE_LEN;

public class PreDigestReader implements ScaleReader<BabePreDigest> {
    @Override
    public BabePreDigest read(ScaleCodecReader reader) {
        BabePreDigest preDigest = new BabePreDigest();
        PreDigestType type = PreDigestType.getByValue(reader.readByte());
        preDigest.setType(type);
        preDigest.setAuthorityIndex(reader.readUint32());
        preDigest.setSlotNumber(new UInt64Reader().read(reader));
        if (!PreDigestType.BABE_SECONDARY_PLAIN.equals(type)) {
            preDigest.setVrfOutput(reader.readByteArray(OUTPUT_BYTE_LEN));
            preDigest.setVrfProof(reader.readByteArray(PROOF_BYTE_LEN));
        }
        return preDigest;
    }
}
