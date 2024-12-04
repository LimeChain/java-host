package com.limechain.babe;

import com.limechain.babe.api.BlockEquivocationProof;
import com.limechain.babe.api.OpaqueKeyOwnershipProof;
import com.limechain.babe.coordinator.SlotChangeEvent;
import com.limechain.babe.coordinator.SlotChangeListener;
import com.limechain.babe.predigest.BabePreDigest;
import com.limechain.babe.state.EpochData;
import com.limechain.babe.state.EpochDescriptor;
import com.limechain.chain.lightsyncstate.Authority;
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

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

@Log
public class BlockProductionVerifier implements SlotChangeListener {

    private final Map<String, BlockHeader> currentSlotAuthorBlockMap = new ConcurrentHashMap<>();

    public boolean verifyAuthorship(Runtime runtime,
                                    BlockHeader blockHeader,
                                    EpochData currentEpochData,
                                    EpochDescriptor descriptor,
                                    BigInteger currentEpochIndex,
                                    BigInteger currentSlotNumber) {
        HeaderDigest[] headerDigests = blockHeader.getDigest();
        Optional<BabePreDigest> babePreDigest = DigestHelper.getBabePreRuntimeDigest(headerDigests);
        HeaderDigest sealDigest = headerDigests[headerDigests.length - 1];
        if (babePreDigest.isEmpty() || !DigestType.SEAL.equals(sealDigest.getType())) {
            return false;
        }

        List<Authority> authorities = currentEpochData.getAuthorities();
        byte[] randomness = currentEpochData.getRandomness();
        byte[] signatureData = sealDigest.getMessage();
        int authorityIndex = babePreDigest.map(BabePreDigest::getAuthorityIndex).map(Math::toIntExact).get();
        Authority verifyingAuthority = authorities.get(authorityIndex);

        if (!checkBlockEquivocation(verifyingAuthority, blockHeader, runtime, currentSlotNumber)) {
            return false;
        }

        VrfOutputAndProof vrfOutputAndProof = VrfOutputAndProof.wrap(babePreDigest.get().getVrfOutput(), babePreDigest.get().getVrfProof());
        VerifySignature signature = new VerifySignature(signatureData, blockHeader.getHashBytes(), verifyingAuthority.getPublicKey(), Key.SR25519);

        boolean isVerified = Sr25519Utils.verifySignature(signature) &&
                verifySlotWinner(authorityIndex, authorities, currentEpochIndex, randomness, descriptor, currentSlotNumber, vrfOutputAndProof);

        if (isVerified) {
            currentSlotAuthorBlockMap.put(StringUtils.toHexWithPrefix(verifyingAuthority.getPublicKey()),blockHeader);
        }

        return isVerified;
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

    private boolean verifySlotWinner(int authorityIndex,
                                     List<Authority> authorities,
                                     BigInteger epochIndex,
                                     byte[] randomness,
                                     EpochDescriptor descriptor,
                                     BigInteger slotNumber,
                                     VrfOutputAndProof vrfOutputAndProof) {
        Authority verifyingAuthority = authorities.get(authorityIndex);
        Schnorrkel.PublicKey publicKey = new Schnorrkel.PublicKey(verifyingAuthority.getPublicKey());

        TranscriptData transcript = Authorship.makeTranscript(randomness, slotNumber, epochIndex);

        BigInteger threshold = Authorship.calculatePrimaryThreshold(
                descriptor.getConstant(),
                authorities,
                authorityIndex);

        var isBelowThreshold = LittleEndianUtils.fromLittleEndianByteArray(Schnorrkel.getInstance().makeBytes(publicKey, transcript, vrfOutputAndProof)).compareTo(threshold) < 0;
        if (!isBelowThreshold) {
            log.log(Level.WARNING, "Block producer is not a winner of the slot");
        }
        return Schnorrkel.getInstance().vrfVerify(publicKey, transcript, vrfOutputAndProof) && isBelowThreshold;
    }

    @Override
    public void slotChanged(SlotChangeEvent event) {
        currentSlotAuthorBlockMap.clear();
    }
}
