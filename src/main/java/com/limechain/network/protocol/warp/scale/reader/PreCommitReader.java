package com.limechain.network.protocol.warp.scale.reader;

import com.limechain.network.protocol.warp.dto.PreCommit;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.scale.ScaleReader;
import io.emeraldpay.polkaj.types.Hash256;
import io.emeraldpay.polkaj.types.Hash512;

public class PreCommitReader implements ScaleReader<PreCommit> {
    @Override
    public PreCommit read(ScaleCodecReader reader) {
        PreCommit preCommit = new PreCommit();
        preCommit.setTargetHash(new Hash256(reader.readUint256()));
        preCommit.setTargetNumber(BlockNumberReader.getInstance().read(reader));
        preCommit.setSignature(new Hash512(reader.readByteArray(64)));
        preCommit.setAuthorityPublicKey(new Hash256(reader.readUint256()));
        return preCommit;
    }
}
