package com.limechain.rpc.methods.author.dto;

import com.limechain.storage.crypto.KeyType;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.scale.ScaleReader;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DecodedKeysReader implements ScaleReader<List<DecodedKey>> {

    private static final DecodedKeysReader INSTANCE = new DecodedKeysReader();

    public static DecodedKeysReader getInstance() {
        return INSTANCE;
    }

    /**
    * https://spec.polkadot.network/chap-runtime-api#id-sessionkeys_decode_session_keys
     */
    @Override
    public List<DecodedKey> read(ScaleCodecReader reader) {
        List<DecodedKey> keys = new ArrayList<>();

        var arraysCount = reader.readByte();
        var pairsInArrayCount = reader.readCompactInt();

        for (int i = 0; i < arraysCount; i++) {
            for (int j = 0; j < pairsInArrayCount; j++) {
                var keySize = reader.readCompactInt();

                var data = reader.readByteArray(keySize);
                var keyType = reader.readByteArray(4);

                keys.add(new DecodedKey(data, KeyType.getByBytes(keyType)));
            }
        }

        return keys;
    }
}