package com.limechain.babe;

import com.limechain.babe.api.BlockEquivocationProof;
import com.limechain.babe.api.OpaqueKeyOwnershipProof;
import com.limechain.babe.coordinator.SlotChangeEvent;
import com.limechain.babe.coordinator.SlotChangeListener;
import com.limechain.babe.predigest.BabePreDigest;
import com.limechain.babe.state.EpochData;
import com.limechain.babe.state.EpochDescriptor;
import com.limechain.chain.lightsyncstate.Authority;
import com.limechain.exception.misc.AuthorshipVerificationException;
import com.limechain.network.protocol.warp.DigestHelper;
import com.limechain.network.protocol.warp.dto.BlockHeader;
import com.limechain.network.protocol.warp.dto.DigestType;
import com.limechain.network.protocol.warp.dto.HeaderDigest;
import com.limechain.runtime.Runtime;
import com.limechain.runtime.hostapi.dto.Key;
import com.limechain.runtime.hostapi.dto.VerifySignature;
import com.limechain.utils.LittleEndianUtils;
import com.limechain.utils.Sr25519Utils;
import com.limechain.utils.StringUtils;
import io.emeraldpay.polkaj.merlin.TranscriptData;
import io.emeraldpay.polkaj.schnorrkel.Schnorrkel;
import io.emeraldpay.polkaj.schnorrkel.VrfOutputAndProof;
import lombok.extern.java.Log;
import org.apache.tomcat.util.buf.HexUtils;
import org.javatuples.Pair;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

@Log
public class BlockProductionVerifier implements SlotChangeListener {
    private final Map<String, BlockHeader> currentSlotAuthorBlockMap = new ConcurrentHashMap<>();

    public boolean isAuthorshipValid(BlockHeader blockHeader,
                                     EpochData currentEpochData,
                                     EpochDescriptor descriptor,
                                     BigInteger currentEpochIndex) {
        HeaderDigest[] headerDigests = blockHeader.getDigest();

        Optional<BabePreDigest> preDigestOptional = DigestHelper.getBabePreRuntimeDigest(headerDigests);
        if (preDigestOptional.isEmpty()) {
            throw new AuthorshipVerificationException("Invalid pre runtime digest.");
        }
        // Seal is always last.
        HeaderDigest sealDigest = headerDigests[headerDigests.length - 1];
        if (!DigestType.SEAL.equals(sealDigest.getType())) {
            throw new AuthorshipVerificationException("Invalid seal digest in header.");
        }

        BabePreDigest preDigest = preDigestOptional.get();
        List<Authority> authorities = currentEpochData.getAuthorities();
        byte[] randomness = currentEpochData.getRandomness();

        if (!isSlotWinnerValid(preDigest, currentEpochIndex, authorities, randomness, descriptor.getConstant())) {
            log.warning(String.format("Author of block No: %s with hash %s is not a valid winner of %s for slot %s",
                    blockHeader.getBlockNumber(),
                    blockHeader.getHash(),
                    preDigest.getType(),
                    preDigest.getSlotNumber()));
            return false;
        }

        // TODO Key should be available before the start of the method so that we avoid duplicate code.
        byte[] authorityPublicKey = getAuthority(authorities, (int) preDigest.getAuthorityIndex())
                .getPublicKey();
        byte[] signatureData = sealDigest.getMessage();
        VerifySignature signature = new VerifySignature(
                signatureData, blockHeader.getBlake2bHash(false), authorityPublicKey, Key.SR25519);

        if (!Sr25519Utils.verifySignature(signature)) {
            log.warning(String.format("Signature of block No: %s with hash %s is not valid",
                    blockHeader.getBlockNumber(),
                    blockHeader.getHash()));
            return false;
        }

        return true;
    }

    private boolean isSlotWinnerValid(BabePreDigest preDigest,
                                      BigInteger currentEpochIndex,
                                      List<Authority> authorities,
                                      byte[] randomness,
                                      Pair<BigInteger, BigInteger> constant) {
        switch (preDigest.getType()) {
            case BABE_PRIMARY -> {
                VrfOutputAndProof vrfOutputAndProof = VrfOutputAndProof.wrap(
                        preDigest.getVrfOutput(), preDigest.getVrfProof());
                return isPrimarySlotWinnerValid((int) preDigest.getAuthorityIndex(),
                        preDigest.getSlotNumber(),
                        authorities,
                        randomness,
                        currentEpochIndex,
                        constant,
                        vrfOutputAndProof);
            }
            case BABE_SECONDARY_VRF -> {
                VrfOutputAndProof vrfOutputAndProof = VrfOutputAndProof.wrap(
                        preDigest.getVrfOutput(), preDigest.getVrfProof());
                return isSecondaryVrfSlotWinnerValid((int) preDigest.getAuthorityIndex(),
                        preDigest.getSlotNumber(),
                        authorities,
                        randomness,
                        currentEpochIndex,
                        vrfOutputAndProof);
            }
            case BABE_SECONDARY_PLAIN -> {
                return isSecondaryPlainSlotWinnerValid((int) preDigest.getAuthorityIndex(),
                        preDigest.getSlotNumber(),
                        authorities,
                        randomness);
            }
            default -> throw new AuthorshipVerificationException("Unexpected pre-digest type: " + preDigest.getType());
        }
    }

    private boolean isPrimarySlotWinnerValid(int authorityIndex,
                                             BigInteger slotNumber,
                                             List<Authority> authorities,
                                             byte[] randomness,
                                             BigInteger epochIndex,
                                             Pair<BigInteger, BigInteger> constant,
                                             VrfOutputAndProof vrfOutputAndProof) {
        Authority verifyingAuthority = getAuthority(authorities, authorityIndex);
        Schnorrkel.PublicKey publicKey = new Schnorrkel.PublicKey(verifyingAuthority.getPublicKey());
        TranscriptData transcript = Authorship.makeTranscript(randomness, slotNumber, epochIndex);

        BigInteger threshold = Authorship.calculatePrimaryThreshold(
                constant,
                authorities,
                authorityIndex);

        var isBelowThreshold = LittleEndianUtils.fromLittleEndianByteArray(
                        Schnorrkel.getInstance().makeBytes(publicKey, transcript, vrfOutputAndProof))
                .compareTo(threshold) < 0;
        if (!isBelowThreshold) {
            return false;
        }

        return Schnorrkel.getInstance().vrfVerify(publicKey, transcript, vrfOutputAndProof);
    }

    private boolean isSecondaryPlainSlotWinnerValid(int authorityIndex,
                                                    BigInteger slotNumber,
                                                    List<Authority> authorities,
                                                    byte[] randomness) {
        Integer expected = Authorship.getSecondarySlotAuthor(randomness, slotNumber, authorities);
        if (expected == null) {
            throw new AuthorshipVerificationException("Something went wrong while getting expected slot author.");
        }

        return expected.compareTo(authorityIndex) == 0;
    }

    private boolean isSecondaryVrfSlotWinnerValid(int authorityIndex,
                                                  BigInteger slotNumber,
                                                  List<Authority> authorities,
                                                  byte[] randomness,
                                                  BigInteger epochIndex,
                                                  VrfOutputAndProof vrfOutputAndProof) {
        Integer expected = Authorship.getSecondarySlotAuthor(randomness, slotNumber, authorities);
        if (expected == null) {
            throw new AuthorshipVerificationException("Something went wrong while getting expected slot author.");
        }

        Authority verifyingAuthority = getAuthority(authorities, authorityIndex);
        Schnorrkel.PublicKey publicKey = new Schnorrkel.PublicKey(verifyingAuthority.getPublicKey());
        TranscriptData transcript = Authorship.makeTranscript(randomness, slotNumber, epochIndex);

        return Schnorrkel.getInstance().vrfVerify(publicKey, transcript, vrfOutputAndProof);
    }

    private Authority getAuthority(List<Authority> authorities, int index) {
        if (index < 0 || index >= authorities.size()) {
            throw new AuthorshipVerificationException("Invalid authority index: " + index);
        }
        return authorities.get(index);
    }

    private boolean checkBlockEquivocation(Authority authority,
                                           BlockHeader blockHeader,
                                           Runtime runtime,
                                           BigInteger currentSlotNumber) {
        String hexPublicKey = HexUtils.toHexString(authority.getPublicKey());
        if (currentSlotAuthorBlockMap.containsKey(hexPublicKey)) {
            BlockHeader firstBlockHeader = currentSlotAuthorBlockMap.get(hexPublicKey);
            if (!firstBlockHeader.equals(blockHeader)) {
                BlockEquivocationProof blockEquivocationProof = new BlockEquivocationProof();
                blockEquivocationProof.setPublicKey(authority.getPublicKey());
                blockEquivocationProof.setSlotNumber(currentSlotNumber);
                blockEquivocationProof.setFirstBlockHeader(firstBlockHeader);
                blockEquivocationProof.setSecondBlockHeader(blockHeader);

                Optional<OpaqueKeyOwnershipProof> opaqueKeyOwnershipProof = runtime.generateKeyOwnershipProof(currentSlotNumber, authority.getPublicKey());
                opaqueKeyOwnershipProof.ifPresentOrElse(
                        key -> runtime.submitReportEquivocationUnsignedExtrinsic(blockEquivocationProof, key.getProof()),
                        () -> log.warning(String.format("Unable to generate Opaque Key Ownership Proof for authority: %s", hexPublicKey)));
                return false;
            }
        }
        return true;
    }

    @Override
    public void slotChanged(SlotChangeEvent event) {
        currentSlotAuthorBlockMap.clear();
    }
}
