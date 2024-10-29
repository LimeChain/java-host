package com.limechain.babe;

import com.limechain.babe.predigest.BabePreDigest;
import com.limechain.babe.predigest.PreDigestType;
import com.limechain.utils.LittleEndianUtils;
import com.limechain.utils.math.BigRational;
import com.limechain.chain.lightsyncstate.Authority;
import io.emeraldpay.polkaj.merlin.TranscriptData;
import io.emeraldpay.polkaj.schnorrkel.Schnorrkel;
import io.emeraldpay.polkaj.schnorrkel.VrfOutputAndProof;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.javatuples.Pair;
import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Authorship {

    public static BabePreDigest claimPrimarySlot(final byte[] randomness,
                                                 final BigInteger slotNumber,
                                                 final BigInteger epochNumber,
                                                 final Schnorrkel.KeyPair keyPair,
                                                 final int authorityIndex,
                                                 final BigInteger threshold) {

        var transcript = makeTranscript(randomness, slotNumber, epochNumber);

        Schnorrkel schnorrkel = Schnorrkel.getInstance();
        VrfOutputAndProof vrfOutputAndProof = schnorrkel.vrfSign(keyPair, transcript);
        byte[] vrfBytes = schnorrkel.makeBytes(keyPair, transcript, vrfOutputAndProof);

        if (vrfBytes.length != 16) {
            throw new IllegalArgumentException("VRF byte array must be exactly 16 bytes long");
        }

        var isBelowThreshold = LittleEndianUtils.fromLittleEndianByteArray(vrfBytes).compareTo(threshold) < 0;

        if (isBelowThreshold) {
            return new BabePreDigest(
                    PreDigestType.BABE_PRIMARY,
                    authorityIndex,
                    slotNumber,
                    vrfOutputAndProof.getOutput(),
                    vrfOutputAndProof.getProof()
            );
        }

        return null;
    }

    // threshold = 2^128 * (1 - (1 - c) ^ (authority_weight / sum(authorities_weights)))
    public static BigInteger calculatePrimaryThreshold(
            @NotNull final Pair<BigInteger, BigInteger> constant,
            @NotNull final List<Authority> authorities,
            final int authorityIndex) {

        double c = getBabeConstant(constant, authorities, authorityIndex);

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

    private static double getBabeConstant(@NotNull Pair<BigInteger, BigInteger> constant,
                                          @NotNull List<Authority> authorities,
                                          int authorityIndex) {

        if (BigInteger.ZERO.equals(constant.getValue1())) {
            throw new IllegalArgumentException("Invalid authority index provided");
        }

        if (authorityIndex >= authorities.size() || authorityIndex < 0) {
            throw new IllegalArgumentException("Invalid denominator provided");
        }

        double numerator = constant.getValue0().doubleValue();
        double denominator = constant.getValue1().doubleValue();
        double c = numerator / denominator;

        if (c > 1 || c < 0) {
            throw new IllegalStateException("BABE constant must be within the range (0, 1)");
        }

        return c;
    }

    private static TranscriptData makeTranscript(byte[] randomness, BigInteger slotNumber, BigInteger epochNumber) {
        var transcript = new TranscriptData("BABE".getBytes());
        transcript.appendMessage("slot number", LittleEndianUtils.toLittleEndianBytes(slotNumber));
        transcript.appendMessage("current epoch", LittleEndianUtils.toLittleEndianBytes(epochNumber));
        transcript.appendMessage("chain randomness", randomness);
        return transcript;
    }
}