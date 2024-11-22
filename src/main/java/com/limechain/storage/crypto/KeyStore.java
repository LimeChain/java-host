package com.limechain.storage.crypto;

import com.limechain.storage.KVRepository;
import io.emeraldpay.polkaj.schnorrkel.Schnorrkel;
import lombok.extern.java.Log;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Component
@Log
public class KeyStore {

    private final KVRepository<String, Object> repository;

    public KeyStore(KVRepository<String, Object> repository) {
        this.repository = repository;
    }

    public void put(KeyType keyType, byte[] publicKey, byte[] privateKey) {
        repository.save(getKey(keyType, publicKey), privateKey);
    }

    public byte[] get(KeyType keyType, byte[] publicKey) {
        return (byte[]) repository.find(getKey(keyType, publicKey)).orElse(null);
    }

    public boolean contains(KeyType keyType, byte[] publicKey) {
        return get(keyType, publicKey) != null;
    }

    /**
     * Retrieves a list of public keys associated with the given key type.
     *
     * @param keyType The type of the key.
     * @return A list of public keys associated with the given key type.
     */
    public List<byte[]> getPublicKeysByKeyType(KeyType keyType) {
        return repository
                .findKeysByPrefix(new String(keyType.getBytes()), 90000)
                .stream()
                .map(this::removeKeyTypeFromKey)
                .toList();
    }

    /**
     * Retrieves a {@link io.emeraldpay.polkaj.schnorrkel.Schnorrkel.KeyPair} from the store.
     *
     * @param type      the algorithm type that the key is used for.
     * @param publicKey the pubKey used as a key to retrieve a privKey from the store.
     * @return the {@link io.emeraldpay.polkaj.schnorrkel.Schnorrkel.KeyPair}.
     */
    public Optional<Schnorrkel.KeyPair> getKeyPair(KeyType type, byte[] publicKey) {
        var privateKey = get(type, publicKey);
        if (privateKey != null) {
            Schnorrkel.PublicKey schnorrkelPubKey = new Schnorrkel.PublicKey(publicKey);
            Schnorrkel.KeyPair keyPair = new Schnorrkel.KeyPair(schnorrkelPubKey, privateKey);
            return Optional.of(keyPair);
        }

        return Optional.empty();
    }

    private byte[] removeKeyTypeFromKey(byte[] key) {
        return Arrays.copyOfRange(key, KeyType.KEY_TYPE_LEN, key.length);
    }

    /**
     * Constructs the key using the key type and key bytes.
     *
     * @param keyType The type of the key.
     * @param key     The key bytes.
     * @return The constructed key string.
     */
    private String getKey(KeyType keyType, byte[] key) {
        return new String(keyType.getBytes()).concat(new String(key));
    }
}
