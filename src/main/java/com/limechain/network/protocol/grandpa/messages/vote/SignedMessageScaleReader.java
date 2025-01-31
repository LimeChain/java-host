package com.limechain.network.protocol.grandpa.messages.vote;

import com.limechain.grandpa.vote.SubRound;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.scale.ScaleReader;
import io.emeraldpay.polkaj.types.Hash256;
import io.emeraldpay.polkaj.types.Hash512;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.math.BigInteger;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SignedMessageScaleReader implements ScaleReader<SignedMessage> {

    private static final SignedMessageScaleReader INSTANCE = new SignedMessageScaleReader();

    public static SignedMessageScaleReader getInstance() {
        return INSTANCE;
    }

    @Override
    public SignedMessage read(ScaleCodecReader reader) {

        SignedMessage signedMessage = new SignedMessage();
        signedMessage.setStage(SubRound.getByStage(reader.readByte()));
        signedMessage.setBlockHash(new Hash256(reader.readUint256()));
        signedMessage.setBlockNumber(BigInteger.valueOf(reader.readUint32()));
        signedMessage.setSignature(new Hash512(reader.readByteArray(64)));
        signedMessage.setAuthorityPublicKey(new Hash256(reader.readUint256()));

        return signedMessage;
    }
}
