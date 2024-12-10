package com.limechain.babe.api.scale;

import com.limechain.babe.api.OpaqueKeyOwnershipProof;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.scale.ScaleReader;

public class OpaqueKeyOwnershipProofReader implements ScaleReader<OpaqueKeyOwnershipProof> {

    @Override
    public OpaqueKeyOwnershipProof read(ScaleCodecReader reader) {
        OpaqueKeyOwnershipProof proof = new OpaqueKeyOwnershipProof();
        proof.setProof(reader.readByteArray());
        return proof;
    }
}
