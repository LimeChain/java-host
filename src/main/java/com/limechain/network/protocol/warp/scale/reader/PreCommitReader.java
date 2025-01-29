package com.limechain.network.protocol.warp.scale.reader;

import com.limechain.network.protocol.warp.dto.PreCommit;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.scale.ScaleReader;
import io.emeraldpay.polkaj.types.Hash256;
import io.emeraldpay.polkaj.types.Hash512;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class PreCommitReader implements ScaleReader<PreCommit> {

    private static final PreCommitReader INSTANCE = new PreCommitReader();

    public static PreCommitReader getInstance() {
        return INSTANCE;
    }

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
