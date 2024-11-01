package com.limechain.utils;

import com.limechain.exception.misc.Sr25519Exception;
import com.limechain.runtime.hostapi.dto.VerifySignature;
import io.emeraldpay.polkaj.schnorrkel.Schnorrkel;
import io.emeraldpay.polkaj.schnorrkel.SchnorrkelException;
import lombok.experimental.UtilityClass;
import lombok.extern.java.Log;
import org.web3j.crypto.MnemonicUtils;

import java.util.logging.Level;

@UtilityClass
@Log
public class Sr25519Utils {

    /**
     * Generates Sr25519 key pair
     * @return KeyPair containing Sr25519 Private key (32 bytes) and Public key (32 bytes)
     */
    public static Schnorrkel.KeyPair generateKeyPair() {
        final Schnorrkel.KeyPair keyPair;
        try {
            keyPair = Schnorrkel.getInstance().generateKeyPair();
        } catch (SchnorrkelException e) {
            throw new Sr25519Exception(e);
        }

        return keyPair;
    }

    /**
     * Generates Sr25519 key pair from mnemonic phrase
     * @param mnemonic BIP-39 12 or 24 word mnemonic phrase
     * @return KeyPair containing Sr25519 Private key (32 bytes) and Public key (32 bytes)
     */
    public static Schnorrkel.KeyPair generateKeyPair(final String mnemonic) {
        byte[] seed = MnemonicUtils.generateSeed(mnemonic, "");
        byte[] secret = new byte[32];
        System.arraycopy(seed, 0, secret, 0, 32);
        final Schnorrkel.KeyPair rootKey;
        try {
            rootKey = Schnorrkel.getInstance().generateKeyPairFromSeed(secret);
        } catch (SchnorrkelException e) {
            throw new Sr25519Exception(e);
        }

        return rootKey;
    }

    /**
     * Loads Sr25519 keypair from byte array
     * @param publicKey 32 bytes Sr25519 public key
     * @param privateKey 32 bytes Sr25519 private key
     * @param message message to be signed
     * @return 64 bytes Sr25519 signature
     */
    public static byte[] signMessage(final byte[] publicKey, final byte[] privateKey, final byte[] message) {
        if (privateKey == null) return null;
        Schnorrkel.KeyPair keyPair = new Schnorrkel.KeyPair(publicKey, privateKey);
        try {
            return Schnorrkel.getInstance().sign(message, keyPair);
        } catch (SchnorrkelException e) {
            log.log(Level.WARNING, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Verifies Sr25519 signature
     * @param signature signature to be verified
     * @return true if signature is valid, false otherwise
     */
    public static boolean verifySignature(final VerifySignature signature) {
        try {
            Schnorrkel schnorrkel = Schnorrkel.getInstance();
            Schnorrkel.PublicKey publicKey = new Schnorrkel.PublicKey(signature.getPublicKeyData());
            return schnorrkel.verify(signature.getSignatureData(), signature.getMessageData(), publicKey);
        } catch (SchnorrkelException e) {
            log.log(Level.WARNING, e.getMessage(), e);
            return false;
        }
    }
}
