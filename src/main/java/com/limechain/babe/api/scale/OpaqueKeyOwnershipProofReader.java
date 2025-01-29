package com.limechain.babe.api.scale;

import com.limechain.babe.api.OpaqueKeyOwnershipProof;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.scale.ScaleReader;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class OpaqueKeyOwnershipProofReader implements ScaleReader<OpaqueKeyOwnershipProof> {

    private static final OpaqueKeyOwnershipProofReader INSTANCE = new OpaqueKeyOwnershipProofReader();

    public static OpaqueKeyOwnershipProofReader getInstance() {
        return INSTANCE;
    }

    @Override
    public OpaqueKeyOwnershipProof read(ScaleCodecReader reader) {
        OpaqueKeyOwnershipProof proof = new OpaqueKeyOwnershipProof();
        proof.setProof(reader.readByteArray());
        return proof;
    }
}
