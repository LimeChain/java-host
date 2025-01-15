package com.limechain.rpc.methods.author;

import com.limechain.exception.global.ExecutionFailedException;
import com.limechain.exception.storage.BlockStorageGenericException;
import com.limechain.exception.transaction.TransactionValidationException;
import com.limechain.rpc.methods.author.dto.DecodedKey;
import com.limechain.runtime.Runtime;
import com.limechain.storage.block.state.BlockState;
import com.limechain.storage.crypto.KeyStore;
import com.limechain.storage.crypto.KeyType;
import com.limechain.transaction.TransactionProcessor;
import com.limechain.transaction.dto.Extrinsic;
import com.limechain.utils.StringUtils;
import com.limechain.utils.scale.ScaleUtils;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.schnorrkel.Schnorrkel;
import io.emeraldpay.polkaj.schnorrkel.SchnorrkelException;
import io.libp2p.crypto.keys.Ed25519PrivateKey;
import lombok.RequiredArgsConstructor;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.javatuples.Pair;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthorRPCImpl {

    private final BlockState blockState;
    private final TransactionProcessor transactionProcessor;
    private final KeyStore keyStore;

    public String authorRotateKeys() {
        Runtime runtime;

        try {
            runtime = blockState.getBestBlockRuntime();
        } catch (BlockStorageGenericException e) {
            throw new ExecutionFailedException("Failed to executed rotate_keys call: " + e.getMessage());
        }

        // The runtime injects the generated keys into the keystore.
        byte[] response = runtime.generateSessionKeys(null);
        return StringUtils.toHexWithPrefix(response);
    }

    public String authorInsertKey(String keyType, String suri, String publicKey) {
        KeyType parsedKeyType = parseKeyType(keyType);

        byte[] privateKey = decodePrivateKey(
                StringUtils.hexToBytes(suri),
                parsedKeyType,
                StringUtils.hexToBytes(publicKey)
        );

        keyStore.put(parsedKeyType, StringUtils.hexToBytes(publicKey), privateKey);
        return publicKey;
    }

    public Boolean authorHasKey(String publicKey, String keyType) {
        KeyType parsedKeyType = parseKeyType(keyType);
        return keyStore.contains(parsedKeyType, StringUtils.hexToBytes(publicKey));
    }

    public Boolean authorHasSessionKeys(String sessionKeys) {
        Runtime runtime;

        try {
            runtime = blockState.getBestBlockRuntime();
        } catch (BlockStorageGenericException e) {
            throw new ExecutionFailedException("Failed to executed has_session_keys call: " + e.getMessage());
        }

        List<DecodedKey> decodedKeys = runtime.decodeSessionKeys(sessionKeys);

        for (DecodedKey decodedKey : decodedKeys) {
            var key = StringUtils.toHexWithPrefix(decodedKey.getData());
            var type = new String(decodedKey.getKeyType().getBytes());

            if (Boolean.FALSE.equals(authorHasKey(key, type))) return false;
        }

        return true;
    }

    public String authorSubmitExtrinsic(String extrinsic) {
        Extrinsic decodedExtrinsic = new Extrinsic(
                ScaleUtils.Decode.decode(
                        StringUtils.hexToBytes(extrinsic),
                        ScaleCodecReader::readByteArray
                )
        );

        try {
            return StringUtils.toHexWithPrefix(
                    transactionProcessor.handleSingleExternalTransaction(decodedExtrinsic, null)
            );
        } catch (TransactionValidationException e) {
            throw new ExecutionFailedException("Failed to executed submit_extrinsic call: " + e.getMessage());
        }
    }

    private byte[] decodePrivateKey(byte[] suri, KeyType keyType, byte[] publicKey) {
        byte[] privateKey;
        byte[] generatedPublicKey;

        switch (keyType.getKey()) {

            case ED25519:
                var ed25519KeyPair = generateEd25519KeyPair(suri);
                generatedPublicKey = ed25519KeyPair.getValue0();
                privateKey = composeEd25519PrivateKey(suri, generatedPublicKey);
                break;

            case SR25519:
                var sr25519KeyPair = generateSr25519KeyPair(suri);
                privateKey = sr25519KeyPair.getSecretKey();
                generatedPublicKey = sr25519KeyPair.getPublicKey();
                break;

            default:
                throw new IllegalArgumentException("Key type not supported");
        }

        validatePublicKey(generatedPublicKey, publicKey);
        return privateKey;
    }

    private Pair<byte[], byte[]> generateEd25519KeyPair(byte[] suri) {
        Ed25519PrivateKeyParameters pkParam = new Ed25519PrivateKeyParameters(suri);
        Ed25519PrivateKey pk = new Ed25519PrivateKey(pkParam);

        return new Pair<>(
                pk.publicKey().raw(),
                pk.raw()
        );
    }

    private byte[] composeEd25519PrivateKey(byte[] seed, byte[] publicKey) {
        byte[] result = new byte[64];
        System.arraycopy(seed, 0, result, 0, seed.length);
        System.arraycopy(publicKey, 0, result, seed.length, publicKey.length);
        return result;
    }

    private Schnorrkel.KeyPair generateSr25519KeyPair(byte[] suri) {
        Schnorrkel schnorrkel = Schnorrkel.getInstance();

        try {
            return schnorrkel.generateKeyPairFromSeed(suri);
        } catch (SchnorrkelException e) {
            throw new IllegalStateException(e.getMessage());
        }
    }

    private void validatePublicKey(byte[] generatedPublicKey, byte[] publicKey) {
        if (!Arrays.equals(generatedPublicKey, publicKey)) {
            throw new IllegalArgumentException("Provided public key or seed is invalid");
        }
    }

    private KeyType parseKeyType(String keyType) {
        KeyType parsedKeyType = KeyType.getByBytes(keyType.getBytes());
        if (parsedKeyType == null) throw new IllegalArgumentException("Invalid key type");
        return parsedKeyType;
    }
}
