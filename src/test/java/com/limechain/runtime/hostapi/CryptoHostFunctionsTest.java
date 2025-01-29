package com.limechain.runtime.hostapi;

import com.limechain.runtime.SharedMemory;
import com.limechain.runtime.hostapi.dto.Key;
import com.limechain.runtime.hostapi.dto.RuntimePointerSize;
import com.limechain.runtime.hostapi.dto.VerifySignature;
import com.limechain.runtime.hostapi.scale.ResultWriter;
import com.limechain.storage.crypto.KeyStore;
import com.limechain.storage.crypto.KeyType;
import com.limechain.utils.EcdsaUtils;
import com.limechain.utils.Ed25519Utils;
import com.limechain.utils.HashUtils;
import com.limechain.utils.Sr25519Utils;
import io.emeraldpay.polkaj.scale.ScaleCodecWriter;
import io.emeraldpay.polkaj.scale.writer.ListWriter;
import io.emeraldpay.polkaj.schnorrkel.Schnorrkel;
import io.libp2p.core.crypto.PrivKey;
import io.libp2p.core.crypto.PubKey;
import io.libp2p.crypto.keys.Ed25519PrivateKey;
import kotlin.Pair;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CryptoHostFunctionsTest {

    private static Ed25519PrivateKey ed25519PrivateKey;
    private static Schnorrkel.KeyPair sr25519KeyPair;
    private static Pair<PrivKey, PubKey> ecdsaKeyPair;
    //Existing seed is 1 and seed bytes
    private final byte[] existingSeed = {1, 73, 1, 101, 110, 104, 97, 110, 99, 101, 32, 112, 97, 110, 105, 99, 32, 97,
            112, 112, 108, 101, 32, 114, 111, 111, 102, 32, 99, 108, 105, 110, 105, 99, 32, 119, 114, 105, 115, 116, 32,
            116, 114, 105, 103, 103, 101, 114, 32, 102, 111, 115, 116, 101, 114, 32, 100, 101, 110, 105, 97, 108, 32,
            105, 109, 105, 116, 97, 116, 101, 32, 114, 101, 115, 111, 117, 114, 99, 101, 32, 103, 114, 97, 105, 110};
    private final byte[] missingSeed = {0};
    private final byte[] signData = {0, 1, 2};
    private final byte[] message = "Test message".getBytes();
    private final byte[] hashedMessage = HashUtils.hashWithBlake2b(message);
    private final int keyPosition = 123;
    private final int publicKeyPosition = 456;
    private final int signaturePosition = 789;
    private final int returnPosition = 999;
    private final RuntimePointerSize keyPointer = new RuntimePointerSize(keyPosition, 4);
    private final RuntimePointerSize publicKeyPointer = new RuntimePointerSize(publicKeyPosition, 32);
    private final RuntimePointerSize signaturePointer = new RuntimePointerSize(signaturePosition, 64);
    private final RuntimePointerSize ecdsaKeyPointer = new RuntimePointerSize(publicKeyPosition, 33);
    String seed = "enhance panic apple roof clinic wrist trigger foster denial imitate resource grain";
    @InjectMocks
    private CryptoHostFunctions cryptoHostFunctions;
    @Spy
    private Set<VerifySignature> signaturesToVerify = new HashSet<>();
    @Mock
    private SharedMemory sharedMemory;
    @Mock
    private RuntimePointerSize seedPointer;
    @Mock
    private RuntimePointerSize messagePointer;
    @Spy
    private RuntimePointerSize returnPointer = new RuntimePointerSize(returnPosition, 0);
    @Mock
    private KeyStore keyStore;

    @BeforeAll
    static void beforeAll() {
        ed25519PrivateKey = Ed25519Utils.generateKeyPair();
        sr25519KeyPair = Sr25519Utils.generateKeyPair();
        ecdsaKeyPair = EcdsaUtils.generateKeyPair();
    }

    @Test
    void ed25519PublicKeysV1() {
        ArrayList<byte[]> pubKeys = new ArrayList<>();
        pubKeys.add(ed25519PrivateKey.publicKey().raw());

        when(sharedMemory.readData(keyPointer)).thenReturn(KeyType.GRANDPA.getBytes());
        when(keyStore.getPublicKeysByKeyType(KeyType.GRANDPA)).thenReturn(pubKeys);
        when(sharedMemory.writeData(toScaleEncoded(pubKeys))).thenReturn(returnPointer);

        RuntimePointerSize result = cryptoHostFunctions.ed25519PublicKeysV1(keyPosition);

        verify(keyStore).getPublicKeysByKeyType(KeyType.GRANDPA);
        assertEquals(returnPointer, result);
    }

    @Test
    void ed25519GenerateV1_no_seed() {
        when(sharedMemory.readData(keyPointer)).thenReturn(KeyType.GRANDPA.getBytes());
        when(sharedMemory.readData(seedPointer)).thenReturn(missingSeed);
        when(sharedMemory.writeData(ed25519PrivateKey.publicKey().raw())).thenReturn(returnPointer);

        try (var ed25519Statick = mockStatic(Ed25519Utils.class)) {
            ed25519Statick.when(Ed25519Utils::generateKeyPair).thenReturn(ed25519PrivateKey);

            int result = cryptoHostFunctions.ed25519GenerateV1(keyPosition, seedPointer);

            ed25519Statick.verify(Ed25519Utils::generateKeyPair);
            assertEquals(returnPointer.pointer(), result);
        }
    }

    @Test
    void ed25519GenerateV1_with_seed() {
        when(sharedMemory.readData(keyPointer)).thenReturn(KeyType.GRANDPA.getBytes());
        when(sharedMemory.readData(seedPointer)).thenReturn(existingSeed);
        when(sharedMemory.writeData(ed25519PrivateKey.publicKey().raw())).thenReturn(returnPointer);

        try (var ed25519Statick = mockStatic(Ed25519Utils.class)) {
            ed25519Statick.when(() -> Ed25519Utils.generateKeyPair(seed)).thenReturn(ed25519PrivateKey);

            int result = cryptoHostFunctions.ed25519GenerateV1(keyPosition, seedPointer);

            ed25519Statick.verify(() -> Ed25519Utils.generateKeyPair(seed));
            assertEquals(returnPointer.pointer(), result);
        }
    }

    @Test
    void ed25519SignV1() {
        when(sharedMemory.readData(keyPointer)).thenReturn(KeyType.GRANDPA.getBytes());
        when(sharedMemory.readData(publicKeyPointer)).thenReturn(ed25519PrivateKey.publicKey().raw());
        when(sharedMemory.readData(messagePointer)).thenReturn(message);
        when(keyStore.get(KeyType.GRANDPA, ed25519PrivateKey.publicKey().raw())).thenReturn(ed25519PrivateKey.raw());
        when(sharedMemory.writeData(scaleEncodedOption(signData))).thenReturn(returnPointer);

        try (var ed25519Statick = mockStatic(Ed25519Utils.class)) {
            ed25519Statick.when(() -> Ed25519Utils.signMessage(ed25519PrivateKey.raw(), message)).thenReturn(signData);
            long result = cryptoHostFunctions.ed25519SignV1(keyPosition, publicKeyPosition, messagePointer);

            assertEquals(returnPointer.pointer(), result);
        }
    }

    @Test
    void ed25519VerifyV1() {
        when(sharedMemory.readData(publicKeyPointer)).thenReturn(ed25519PrivateKey.publicKey().raw());
        when(sharedMemory.readData(messagePointer)).thenReturn(message);
        when(sharedMemory.readData(signaturePointer)).thenReturn(ed25519PrivateKey.sign(message));

        int verify = cryptoHostFunctions.ed25519VerifyV1(signaturePosition, messagePointer, publicKeyPosition);
        assertEquals(1, verify);
    }

    @Test
    void ed25519BatchVerifyV1_with_verification_off() {
        when(sharedMemory.readData(publicKeyPointer)).thenReturn(ed25519PrivateKey.publicKey().raw());
        when(sharedMemory.readData(messagePointer)).thenReturn(message);
        byte[] signed = ed25519PrivateKey.sign(message);
        when(sharedMemory.readData(signaturePointer)).thenReturn(signed);

        assertFalse(cryptoHostFunctions.batchVerificationStarted);

        int verify = cryptoHostFunctions.ed25519BatchVerifyV1(signaturePosition, messagePointer, publicKeyPosition);

        verifyNoInteractions(signaturesToVerify);
        assertEquals(1, verify);
    }

    @Test
    void ed25519BatchVerifyV1_with_verification_on() {
        cryptoHostFunctions.batchVerificationStarted = true;
        byte[] pubKey = ed25519PrivateKey.publicKey().raw();
        VerifySignature sig = new VerifySignature(signData, message, pubKey, Key.ED25519);

        when(sharedMemory.readData(publicKeyPointer)).thenReturn(pubKey);
        when(sharedMemory.readData(messagePointer)).thenReturn(message);
        when(sharedMemory.readData(signaturePointer)).thenReturn(signData);

        assertTrue(cryptoHostFunctions.batchVerificationStarted);

        int verify = cryptoHostFunctions.ed25519BatchVerifyV1(signaturePosition, messagePointer, publicKeyPosition);

        verify(signaturesToVerify).add(sig);
        assertEquals(1, verify);
    }

    @Test
    void sr25519PublicKeysV1() {
        ArrayList<byte[]> pubKeys = new ArrayList<>();
        pubKeys.add(sr25519KeyPair.getPublicKey());

        when(sharedMemory.readData(keyPointer)).thenReturn(KeyType.BABE.getBytes());
        when(keyStore.getPublicKeysByKeyType(KeyType.BABE)).thenReturn(pubKeys);
        when(sharedMemory.writeData(toScaleEncoded(pubKeys))).thenReturn(returnPointer);

        RuntimePointerSize result = cryptoHostFunctions.sr25519PublicKeysV1(keyPosition);

        verify(keyStore).getPublicKeysByKeyType(KeyType.BABE);
        assertEquals(returnPointer, result);
    }

    @Test
    void sr25519GenerateV1_no_seed() {
        when(sharedMemory.readData(keyPointer)).thenReturn(KeyType.BABE.getBytes());
        when(sharedMemory.readData(seedPointer)).thenReturn(missingSeed);
        when(sharedMemory.writeData(sr25519KeyPair.getPublicKey())).thenReturn(returnPointer);

        try (var sr25519Statick = mockStatic(Sr25519Utils.class)) {
            sr25519Statick.when(Sr25519Utils::generateKeyPair).thenReturn(sr25519KeyPair);

            int result = cryptoHostFunctions.sr25519GenerateV1(keyPosition, seedPointer);

            sr25519Statick.verify(Sr25519Utils::generateKeyPair);
            assertEquals(returnPointer.pointer(), result);
        }
    }

    @Test
    void sr25519GenerateV1_with_seed() {
        when(sharedMemory.readData(keyPointer)).thenReturn(KeyType.BABE.getBytes());
        when(sharedMemory.readData(seedPointer)).thenReturn(existingSeed);
        when(sharedMemory.writeData(sr25519KeyPair.getPublicKey())).thenReturn(returnPointer);

        try (var sr25519Statick = mockStatic(Sr25519Utils.class)) {
            sr25519Statick.when(() -> Sr25519Utils.generateKeyPair(seed)).thenReturn(sr25519KeyPair);

            int result = cryptoHostFunctions.sr25519GenerateV1(keyPosition, seedPointer);

            sr25519Statick.verify(() -> Sr25519Utils.generateKeyPair(seed));
            assertEquals(returnPointer.pointer(), result);
        }
    }

    @Test
    void sr25519SignV1() {
        when(sharedMemory.readData(keyPointer)).thenReturn(KeyType.BABE.getBytes());
        when(sharedMemory.readData(publicKeyPointer)).thenReturn(sr25519KeyPair.getPublicKey());
        when(sharedMemory.readData(messagePointer)).thenReturn(message);
        when(keyStore.get(KeyType.BABE, sr25519KeyPair.getPublicKey())).thenReturn(sr25519KeyPair.getSecretKey());
        when(sharedMemory.writeData(scaleEncodedOption(signData))).thenReturn(returnPointer);

        try (var sr25519Statick = mockStatic(Sr25519Utils.class)) {
            sr25519Statick
                    .when(() ->
                            Sr25519Utils.signMessage(sr25519KeyPair.getPublicKey(), sr25519KeyPair.getSecretKey(),
                                    message))
                    .thenReturn(signData);
            int result = cryptoHostFunctions.sr25519SignV1(keyPosition, publicKeyPosition, messagePointer);

            assertEquals(returnPointer.pointer(), result);
        }
    }

    @Test
    void sr25519VerifyV1() {
        when(sharedMemory.readData(publicKeyPointer)).thenReturn(sr25519KeyPair.getPublicKey());
        when(sharedMemory.readData(messagePointer)).thenReturn(message);
        byte[] sig = Sr25519Utils.signMessage(sr25519KeyPair.getPublicKey(), sr25519KeyPair.getSecretKey(), message);
        when(sharedMemory.readData(signaturePointer)).thenReturn(sig);

        int verify = cryptoHostFunctions.sr25519VerifyV1(signaturePosition, messagePointer, publicKeyPosition);
        assertEquals(1, verify);
    }

    @Test
    void sr25519BatchVerifyV1_with_verification_off() {
        when(sharedMemory.readData(publicKeyPointer)).thenReturn(sr25519KeyPair.getPublicKey());
        when(sharedMemory.readData(messagePointer)).thenReturn(message);
        byte[] sig = Sr25519Utils.signMessage(sr25519KeyPair.getPublicKey(), sr25519KeyPair.getSecretKey(), message);
        when(sharedMemory.readData(signaturePointer)).thenReturn(sig);

        assertFalse(cryptoHostFunctions.batchVerificationStarted);

        int verify = cryptoHostFunctions.sr25519BatchVerifyV1(signaturePosition, messagePointer, publicKeyPosition);

        verifyNoInteractions(signaturesToVerify);
        assertEquals(1, verify);
    }

    @Test
    void sr25519BatchVerifyV1_with_verification_on() {
        cryptoHostFunctions.batchVerificationStarted = true;
        byte[] pubKey = sr25519KeyPair.getPublicKey();
        ;
        VerifySignature sig = new VerifySignature(signData, message, pubKey, Key.SR25519);

        when(sharedMemory.readData(publicKeyPointer)).thenReturn(sr25519KeyPair.getPublicKey());
        when(sharedMemory.readData(messagePointer)).thenReturn(message);
        when(sharedMemory.readData(signaturePointer)).thenReturn(signData);

        assertTrue(cryptoHostFunctions.batchVerificationStarted);

        int verify = cryptoHostFunctions.sr25519BatchVerifyV1(signaturePosition, messagePointer, publicKeyPosition);

        verify(signaturesToVerify).add(sig);
        assertEquals(1, verify);
    }

    @Test
    void ecdsaPublicKeysV1() {
        ArrayList<byte[]> pubKeys = new ArrayList<>();
        pubKeys.add(ecdsaKeyPair.getSecond().raw());

        when(sharedMemory.readData(keyPointer)).thenReturn(KeyType.BEEFY.getBytes());
        when(keyStore.getPublicKeysByKeyType(KeyType.BEEFY)).thenReturn(pubKeys);
        when(sharedMemory.writeData(toScaleEncoded(pubKeys))).thenReturn(returnPointer);

        RuntimePointerSize result = cryptoHostFunctions.ecdsaPublicKeysV1(keyPosition);

        verify(keyStore).getPublicKeysByKeyType(KeyType.BEEFY);
        assertEquals(returnPointer, result);
    }

    @Test
    void ecdsaGenerateV1_no_seed() {
        when(sharedMemory.readData(keyPointer)).thenReturn(KeyType.BEEFY.getBytes());
        when(sharedMemory.readData(seedPointer)).thenReturn(missingSeed);
        when(sharedMemory.writeData(ecdsaKeyPair.getSecond().raw())).thenReturn(returnPointer);

        try (var ecdsaUtil = mockStatic(EcdsaUtils.class)) {
            ecdsaUtil.when(EcdsaUtils::generateKeyPair).thenReturn(ecdsaKeyPair);

            int result = cryptoHostFunctions.ecdsaGenerateV1(keyPosition, seedPointer);

            ecdsaUtil.verify(EcdsaUtils::generateKeyPair);
            assertEquals(returnPointer.pointer(), result);
        }
    }

    @Test
    void ecdsaGenerateV1_use_seed() {
        when(sharedMemory.readData(keyPointer)).thenReturn(KeyType.BEEFY.getBytes());
        when(sharedMemory.readData(seedPointer)).thenReturn(existingSeed);
        when(sharedMemory.writeData(ecdsaKeyPair.getSecond().raw())).thenReturn(returnPointer);

        try (var ecdsaUtil = mockStatic(EcdsaUtils.class)) {
            ecdsaUtil.when(() -> EcdsaUtils.generateKeyPair(seed)).thenReturn(ecdsaKeyPair);

            int result = cryptoHostFunctions.ecdsaGenerateV1(keyPosition, seedPointer);

            ecdsaUtil.verify(() -> EcdsaUtils.generateKeyPair(seed));
            assertEquals(returnPointer.pointer(), result);
        }
    }

    @Test
    void ecdsaSignV1() {
        when(sharedMemory.readData(keyPointer)).thenReturn(KeyType.CONTROLLING_ACCOUNTS.getBytes());
        when(sharedMemory.readData(ecdsaKeyPointer)).thenReturn(ecdsaKeyPair.getSecond().raw());
        when(sharedMemory.readData(messagePointer)).thenReturn(message);
        when(keyStore.get(KeyType.CONTROLLING_ACCOUNTS, ecdsaKeyPair.getSecond().raw()))
                .thenReturn(ecdsaKeyPair.getFirst().raw());
        when(sharedMemory.writeData(scaleEncodedOption(signData))).thenReturn(returnPointer);

        try (var ecdsaStatic = mockStatic(EcdsaUtils.class)) {
            ecdsaStatic.when(() ->
                    EcdsaUtils.signMessage(ecdsaKeyPair.getFirst().raw(), hashedMessage)).thenReturn(signData);
            int result = cryptoHostFunctions.ecdsaSignV1(keyPosition, publicKeyPosition, messagePointer);

            assertEquals(returnPointer.pointer(), result);
        }
    }

    @Test
    void ecdsaSignPrehashedV1() {
        when(sharedMemory.readData(keyPointer)).thenReturn(KeyType.CONTROLLING_ACCOUNTS.getBytes());
        when(sharedMemory.readData(ecdsaKeyPointer)).thenReturn(ecdsaKeyPair.getSecond().raw());
        when(sharedMemory.readData(messagePointer)).thenReturn(hashedMessage);
        when(keyStore.get(KeyType.CONTROLLING_ACCOUNTS, ecdsaKeyPair.getSecond().raw()))
                .thenReturn(ecdsaKeyPair.getFirst().raw());
        when(sharedMemory.writeData(scaleEncodedOption(signData))).thenReturn(returnPointer);

        try (var ecdsaStatic = mockStatic(EcdsaUtils.class)) {
            ecdsaStatic.when(() ->
                    EcdsaUtils.signMessage(ecdsaKeyPair.getFirst().raw(), hashedMessage)).thenReturn(signData);
            int result = cryptoHostFunctions.ecdsaSignPrehashedV1(keyPosition, publicKeyPosition, messagePointer);

            assertEquals(returnPointer.pointer(), result);
        }
    }

    @Test
    void ecdsaVerifyV1() {
        when(sharedMemory.readData(ecdsaKeyPointer)).thenReturn(ecdsaKeyPair.getSecond().raw());
        when(sharedMemory.readData(messagePointer)).thenReturn(message);
        byte[] sig = EcdsaUtils.signMessage(ecdsaKeyPair.getFirst().raw(), hashedMessage);
        when(sharedMemory.readData(signaturePointer)).thenReturn(sig);

        int verify = cryptoHostFunctions.ecdsaVerifyV1(signaturePosition, messagePointer, publicKeyPosition);
        assertEquals(1, verify);
    }

    @Test
    void ecdsaVerifyPrehashedV1() {
        when(sharedMemory.readData(ecdsaKeyPointer)).thenReturn(ecdsaKeyPair.getSecond().raw());
        when(sharedMemory.readData(new RuntimePointerSize(keyPosition, 32))).thenReturn(hashedMessage);
        byte[] sig = EcdsaUtils.signMessage(ecdsaKeyPair.getFirst().raw(), hashedMessage);
        when(sharedMemory.readData(signaturePointer)).thenReturn(sig);

        int verify = cryptoHostFunctions.ecdsaVerifyPrehashedV1(signaturePosition, keyPosition, publicKeyPosition);
        assertEquals(1, verify);
    }

    @Test
    void ecdsaBatchVerifyV1_with_verification_off() {
        when(sharedMemory.readData(ecdsaKeyPointer)).thenReturn(ecdsaKeyPair.getSecond().raw());
        when(sharedMemory.readData(messagePointer)).thenReturn(message);
        byte[] sig = EcdsaUtils.signMessage(ecdsaKeyPair.getFirst().raw(), hashedMessage);
        when(sharedMemory.readData(signaturePointer)).thenReturn(sig);

        assertFalse(cryptoHostFunctions.batchVerificationStarted);

        int verify = cryptoHostFunctions.ecdsaBatchVerifyV1(signaturePosition, messagePointer, publicKeyPosition);

        verifyNoInteractions(signaturesToVerify);
        assertEquals(1, verify);
    }

    @Test
    void ecdsaBatchVerifyV1_with_verification_on() {
        cryptoHostFunctions.batchVerificationStarted = true;
        byte[] pubKey = ecdsaKeyPair.getSecond().raw();
        VerifySignature sig = new VerifySignature(signData, hashedMessage, pubKey, Key.ECDSA);

        when(sharedMemory.readData(ecdsaKeyPointer)).thenReturn(ecdsaKeyPair.getSecond().raw());
        when(sharedMemory.readData(messagePointer)).thenReturn(message);
        when(sharedMemory.readData(signaturePointer)).thenReturn(signData);

        assertTrue(cryptoHostFunctions.batchVerificationStarted);

        int verify = cryptoHostFunctions.ecdsaBatchVerifyV1(signaturePosition, messagePointer, publicKeyPosition);

        verify(signaturesToVerify).add(sig);
        assertEquals(1, verify);
    }

    @Test
    void secp256k1EcdsaRecoverV1() {
        when(sharedMemory.readData(new RuntimePointerSize(signaturePosition, 65))).thenReturn(signData);
        when(sharedMemory.readData(new RuntimePointerSize(keyPosition, 32))).thenReturn(hashedMessage);
        when(sharedMemory.writeData(toEncodedResult(ecdsaKeyPair.getSecond().raw()))).thenReturn(returnPointer);

        try (var ecdsaStatic = mockStatic(EcdsaUtils.class)) {
            ecdsaStatic.when(() ->
                            EcdsaUtils.recoverPublicKeyFromSignature(signData, hashedMessage, false))
                    .thenReturn(ecdsaKeyPair.getSecond().raw());

            long result = cryptoHostFunctions.secp256k1EcdsaRecoverV1(signaturePosition, keyPosition);

            assertEquals(returnPointer.pointer(), result);
        }
    }

    @Test
    void secp256k1EcdsaRecoverCompressedV1() {
        when(sharedMemory.readData(new RuntimePointerSize(signaturePosition, 65))).thenReturn(signData);
        when(sharedMemory.readData(new RuntimePointerSize(keyPosition, 32))).thenReturn(hashedMessage);
        when(sharedMemory.writeData(toEncodedResult(ecdsaKeyPair.getSecond().raw()))).thenReturn(returnPointer);

        try (var ecdsaStatic = mockStatic(EcdsaUtils.class)) {
            ecdsaStatic.when(() ->
                            EcdsaUtils.recoverPublicKeyFromSignature(signData, hashedMessage, true))
                    .thenReturn(ecdsaKeyPair.getSecond().raw());

            long result = cryptoHostFunctions.secp256k1EcdsaRecoverCompressedV1(signaturePosition, keyPosition);

            assertEquals(returnPointer.pointer(), result);
        }
    }

    @Test
    void startBatchVerify() {
        assertFalse(cryptoHostFunctions.batchVerificationStarted);

        cryptoHostFunctions.startBatchVerify();

        assertTrue(cryptoHostFunctions.batchVerificationStarted);
    }

    @Test()
    void finishBatchVerify() {
        cryptoHostFunctions.batchVerificationStarted = true;

        int allValid = cryptoHostFunctions.finishBatchVerify();

        assertEquals(1, allValid);
        assertFalse(cryptoHostFunctions.batchVerificationStarted);
    }

    private byte[] toScaleEncoded(List<byte[]> toEncode) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ScaleCodecWriter scaleWriter = new ScaleCodecWriter(baos)) {

            ListWriter<byte[]> listWriter = new ListWriter<>(ScaleCodecWriter::writeByteArray);
            listWriter.write(scaleWriter, toEncode);
            return baos.toByteArray();
        } catch (IOException e) {
            return new byte[]{};
        }
    }

    private byte[] scaleEncodedOption(byte[] data) {
        try (ByteArrayOutputStream buf = new ByteArrayOutputStream();
             ScaleCodecWriter writer = new ScaleCodecWriter(buf)) {
            writer.writeOptional(ScaleCodecWriter::writeByteArray, data);
            return buf.toByteArray();
        } catch (IOException e) {
            return new byte[]{};
        }
    }

    private byte[] toEncodedResult(byte[] data) {
        try (ByteArrayOutputStream buf = new ByteArrayOutputStream();
             ScaleCodecWriter writer = new ScaleCodecWriter(buf)) {
            ResultWriter resultWriter = ResultWriter.getInstance();
            resultWriter.writeResult(writer, true);
            resultWriter.write(writer, data);
            return buf.toByteArray();
        } catch (IOException e) {
            return new byte[]{};
        }
    }
}