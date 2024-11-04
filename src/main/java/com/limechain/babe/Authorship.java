package com.limechain.babe;

import com.limechain.babe.predigest.BabePreDigest;
import com.limechain.babe.predigest.PreDigestType;
import com.limechain.babe.state.EpochState;
import com.limechain.chain.lightsyncstate.BabeEpoch;
import com.limechain.storage.crypto.KeyStore;
import com.limechain.storage.crypto.KeyType;
import com.limechain.utils.ByteArrayUtils;
import com.limechain.utils.LittleEndianUtils;
import com.limechain.utils.math.BigRational;
import com.limechain.chain.lightsyncstate.Authority;
import io.emeraldpay.polkaj.merlin.TranscriptData;
import io.emeraldpay.polkaj.schnorrkel.Schnorrkel;
import io.emeraldpay.polkaj.schnorrkel.VrfOutputAndProof;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.java.Log;
import org.apache.commons.collections4.map.LinkedMap;
import org.bouncycastle.jcajce.provider.digest.Blake2b;
import org.javatuples.Pair;
import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

@Log
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Authorship {

    public static BabePreDigest claimSlot(EpochState epochState, KeyStore keyStore) {

        var randomness = epochState.getCurrentEpochData().getRandomness();
        var slotNumber = epochState.getCurrentSlotNumber();
        var epochIndex = epochState.getEpochIndex();
        var c = epochState.getCurrentEpochDescriptor().getConstant();
        var authorities = epochState.getCurrentEpochData().getAuthorities();
        var allowedSlots = epochState.getCurrentEpochDescriptor().getAllowedSlots();

        var indexKeyPairMap = getOwnedKeyPairsFromAuthoritySet(authorities, keyStore);

        BabePreDigest primarySlot = claimPrimarySlot(
                randomness,
                slotNumber,
                epochIndex,
                authorities,
                c,
                indexKeyPairMap
        );

        if (primarySlot != null) return primarySlot;

        boolean authorSecondaryVrfSlot = allowedSlots.equals(BabeEpoch.BabeAllowedSlots.PRIMARY_AND_SECONDARY_VRF_SLOTS);

        return claimSecondarySlot(
                randomness,
                slotNumber,
                epochIndex,
                authorities,
                indexKeyPairMap,
                authorSecondaryVrfSlot
        );
    }

    private static BabePreDigest claimPrimarySlot(final byte[] randomness,
                                                  final BigInteger slotNumber,
                                                  final BigInteger epochIndex,
                                                  final List<Authority> authorities,
                                                  final Pair<BigInteger, BigInteger> c,
                                                  final Map<Integer, Schnorrkel.KeyPair> indexKeyPairMap) {

        var transcript = makeTranscript(randomness, slotNumber, epochIndex);

        for (Map.Entry<Integer, Schnorrkel.KeyPair> entry : indexKeyPairMap.entrySet()) {

            var authorityIndex = entry.getKey();
            var keyPair = entry.getValue();

            var threshold = calculatePrimaryThreshold(c, authorities, authorityIndex);

            Schnorrkel schnorrkel = Schnorrkel.getInstance();
            VrfOutputAndProof vrfOutputAndProof = schnorrkel.vrfSign(keyPair, transcript);
            byte[] vrfBytes = schnorrkel.makeBytes(keyPair, transcript, vrfOutputAndProof);

            if (vrfBytes.length != 16) {
                throw new IllegalArgumentException("VRF byte array must be exactly 16 bytes long");
            }

            var isBelowThreshold = LittleEndianUtils.fromLittleEndianByteArray(vrfBytes).compareTo(threshold) < 0;

            if (isBelowThreshold) {
                log.log(Level.FINE, "Primary slot successfully claimed for slot number: {}", slotNumber);

                return new BabePreDigest(
                        PreDigestType.BABE_PRIMARY,
                        authorityIndex.longValue(),
                        slotNumber,
                        vrfOutputAndProof.getOutput(),
                        vrfOutputAndProof.getProof()
                );
            }
        }

        return null;
    }

    private static BabePreDigest claimSecondarySlot(final byte[] randomness,
                                                    final BigInteger slotNumber,
                                                    final BigInteger epochIndex,
                                                    final List<Authority> authorities,
                                                    final Map<Integer, Schnorrkel.KeyPair> indexKeyPairMap,
                                                    final boolean authorSecondaryVrfSlot) {

        var secondarySlotAuthorIndex = getSecondarySlotAuthor(randomness, slotNumber, authorities);
        if (secondarySlotAuthorIndex == null) {
            return null;
        }

        for (Map.Entry<Integer, Schnorrkel.KeyPair> entry : indexKeyPairMap.entrySet()) {

            var authorityIndex = entry.getKey();
            var keyPair = entry.getValue();

            if (!secondarySlotAuthorIndex.equals(authorityIndex)) {
                return null;
            }

            if (authorSecondaryVrfSlot) {
                log.log(Level.FINE, "Secondary VRF slot successfully claimed for slot number: {}", slotNumber);

                return buildSecondaryVrfPreDigest(
                        randomness,
                        slotNumber,
                        epochIndex,
                        keyPair,
                        authorityIndex
                );
            } else {
                log.log(Level.FINE, "Secondary Plain slot successfully claimed for slot number: {}", slotNumber);

                return new BabePreDigest(
                        PreDigestType.BABE_SECONDARY_PLAIN,
                        authorityIndex.longValue(),
                        slotNumber,
                        null,
                        null
                );
            }
        }

        return null;
    }

    private static BabePreDigest buildSecondaryVrfPreDigest(final byte[] randomness,
                                                            final BigInteger slotNumber,
                                                            final BigInteger epochIndex,
                                                            final Schnorrkel.KeyPair keyPair,
                                                            final int authorityIndex) {

        var transcript = makeTranscript(randomness, slotNumber, epochIndex);
        VrfOutputAndProof vrfOutputAndProof = Schnorrkel.getInstance().vrfSign(keyPair, transcript);

        return new BabePreDigest(
                PreDigestType.BABE_SECONDARY_VRF,
                authorityIndex,
                slotNumber,
                vrfOutputAndProof.getOutput(),
                vrfOutputAndProof.getProof()
        );
    }

    private static Integer getSecondarySlotAuthor(final byte[] randomness,
                                                  final BigInteger slotNumber,
                                                  final List<Authority> authorities) {
        if (authorities.isEmpty()) return null;

        byte[] concat = ByteArrayUtils.concatenate(randomness, slotNumber.toByteArray());

        Blake2b.Blake2b256 blake2b256 = new Blake2b.Blake2b256();
        byte[] hash = blake2b256.digest(concat);

        var rand = LittleEndianUtils.fromLittleEndianByteArray(hash);
        var authoritiesCount = BigInteger.valueOf(authorities.size());
        var authorityIndex = rand.mod(authoritiesCount);

        if (authorityIndex.compareTo(authoritiesCount) < 0) {
            return authorityIndex.intValue();
        }

        return null;
    }

    // threshold = 2^128 * (1 - (1 - c) ^ (authority_weight / sum(authorities_weights)))
    private static BigInteger calculatePrimaryThreshold(
            @NotNull final Pair<BigInteger, BigInteger> constant,
            @NotNull final List<Authority> authorities,
            final int authorityIndex) {

        if (authorityIndex >= authorities.size() || authorityIndex < 0) {
            throw new IllegalArgumentException("Invalid denominator provided");
        }

        double c = getBabeConstant(constant);

        double totalWeight = authorities.stream()
                .map(Authority::getWeight)
                .reduce(BigInteger.ZERO, BigInteger::add)
                .doubleValue();

        double weight = authorities.get(authorityIndex)
                .getWeight()
                .doubleValue();

        double theta = weight / totalWeight;

        // p = 1 - (1 - c) ^ theta
        double p = 1.0 - Math.pow((1.0 - c), theta);

        BigRational pRational = new BigRational(p);

        // 1<<128 == 2^128
        BigInteger twoToThe128 = BigInteger.ONE.shiftLeft(128);
        BigInteger scaledNumer = twoToThe128.multiply(pRational.getNumerator());

        return scaledNumer.divide(pRational.getDenominator());
    }

    private static Map<Integer, Schnorrkel.KeyPair> getOwnedKeyPairsFromAuthoritySet(final List<Authority> authorities,
                                                                                     final KeyStore keyStore) {

        Map<Integer, Schnorrkel.KeyPair> indexKeyPairMap = new LinkedMap<>();

        for (Authority authority : authorities) {
            var privateKey = keyStore.get(KeyType.BABE, authority.getPublicKey());
            if (privateKey != null) {
                Schnorrkel.PublicKey publicKey = new Schnorrkel.PublicKey(authority.getPublicKey());
                Schnorrkel.KeyPair keyPair = new Schnorrkel.KeyPair(publicKey, privateKey);
                indexKeyPairMap.put(authorities.indexOf(authority), keyPair);
            }
        }

        return indexKeyPairMap;
    }

    private static double getBabeConstant(@NotNull final Pair<BigInteger, BigInteger> constant) {

        if (BigInteger.ZERO.equals(constant.getValue1())) {
            throw new IllegalArgumentException("Invalid authority index provided");
        }

        double numerator = constant.getValue0().doubleValue();
        double denominator = constant.getValue1().doubleValue();
        double c = numerator / denominator;

        if (c > 1 || c < 0) {
            throw new IllegalStateException("BABE constant must be within the range (0, 1)");
        }

        return c;
    }

    private static TranscriptData makeTranscript(byte[] randomness, BigInteger slotNumber, BigInteger epochIndex) {
        var transcript = new TranscriptData("BABE".getBytes());
        transcript.appendMessage("slot number", LittleEndianUtils.toLittleEndianBytes(slotNumber));
        transcript.appendMessage("current epoch", LittleEndianUtils.toLittleEndianBytes(epochIndex));
        transcript.appendMessage("chain randomness", randomness);
        return transcript;
    }
}