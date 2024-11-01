package com.limechain.storage.crypto;

import com.limechain.chain.Chain;
import com.limechain.storage.DBInitializer;
import com.limechain.storage.DBRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class KeyStoreTest {

    private static final byte[] KEY = {1, 2, 3};
    private static final byte[] KEY_2 = {4, 5, 6};
    private static final byte[] KEY_3 = {7, 8, 9};
    private static final byte[] KEY_4 = {10, 11, 12};
    private static final byte[] VALUE = {1, 2, 3, 4};
    private static final byte[] VALUE_2 = {5, 6, 7, 8};
    private static final byte[] VALUE_3 = {9, 10, 11, 12};
    private static final byte[] VALUE_4 = {13, 14, 15, 16};

    private DBRepository dbRepository;
    private KeyStore keyStore;

    @BeforeEach
    public void setup() {
        dbRepository = DBInitializer.initialize("./test", Chain.WESTEND, true);
        keyStore = new KeyStore(dbRepository);
    }

    @Test
    void saveAndGetKey() {
        keyStore.put(KeyType.BABE, KEY, VALUE);
        keyStore.put(KeyType.GRANDPA, KEY_2, VALUE_2);
        keyStore.put(KeyType.BABE, KEY_3, VALUE_3);
        keyStore.put(KeyType.BEEFY, KEY_4, VALUE_4);

        byte[] privKey = keyStore.get(KeyType.BABE, KEY);
        byte[] privKey2 = keyStore.get(KeyType.GRANDPA, KEY_2);
        byte[] privKey3 = keyStore.get(KeyType.BABE, KEY_3);
        byte[] privKey4 = keyStore.get(KeyType.BEEFY, KEY_4);

        assertArrayEquals(VALUE, privKey);
        assertArrayEquals(VALUE_2, privKey2);
        assertArrayEquals(VALUE_3, privKey3);
        assertArrayEquals(VALUE_4, privKey4);

        byte[] invPrivKey = keyStore.get(KeyType.GRANDPA, KEY_4);
        byte[] invPrivKey2 = keyStore.get(KeyType.BABE, KEY_2);
        byte[] invPrivKey3 = keyStore.get(KeyType.GRANDPA, KEY_3);
        byte[] invPrivKey4 = keyStore.get(KeyType.BEEFY, KEY);

        assertNull(invPrivKey);
        assertNull(invPrivKey2);
        assertNull(invPrivKey3);
        assertNull(invPrivKey4);
    }

    @Test
    void saveAndGetCommonKey() {
        keyStore.put(KeyType.BABE, KEY, VALUE);
        keyStore.put(KeyType.GRANDPA, KEY_2, VALUE_2);
        keyStore.put(KeyType.BABE, KEY_3, VALUE_3);
        keyStore.put(KeyType.BEEFY, KEY_4, VALUE_4);

        List<byte[]> publicKeysByKeyType = keyStore.getPublicKeysByKeyType(KeyType.BABE);

        assertEquals(2, publicKeysByKeyType.size());

        List<byte[]> publicKeysByKeyTypeGrandpa = keyStore.getPublicKeysByKeyType(KeyType.GRANDPA);
        assertEquals(publicKeysByKeyTypeGrandpa.get(0).length, KEY_2.length);
    }

}
