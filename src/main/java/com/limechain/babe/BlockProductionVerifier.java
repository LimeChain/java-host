package com.limechain.babe;

import com.limechain.babe.state.EpochState;
import com.limechain.chain.lightsyncstate.Authority;
import com.limechain.rpc.server.AppBean;
import com.limechain.utils.LittleEndianUtils;
import io.emeraldpay.polkaj.merlin.TranscriptData;
import io.emeraldpay.polkaj.schnorrkel.Schnorrkel;
import io.emeraldpay.polkaj.schnorrkel.VrfOutputAndProof;
import lombok.extern.java.Log;

import java.math.BigInteger;
import java.util.List;
import java.util.logging.Level;

@Log
public class BlockProductionVerifier {

    private EpochState epochState = AppBean.getBean(EpochState.class);

    public boolean verifySlotWinner(int authorityIndex, BigInteger epochIndex, byte[] randomness, BigInteger slotNumber, VrfOutputAndProof vrfOutputAndProof) {
        List<Authority> authorities = epochState.getCurrentEpochData().getAuthorities();
        Authority verifyingAuthority = authorities.get(authorityIndex);
        TranscriptData transcriptData = Authorship.makeTranscript(randomness, slotNumber, epochIndex);
        BigInteger threshold = Authorship.calculatePrimaryThreshold(epochState.getCurrentEpochDescriptor().getConstant(), authorities, authorityIndex);
        var isBelowThreshold = LittleEndianUtils.fromLittleEndianByteArray(vrfOutputAndProof.getOutput()).compareTo(threshold) < 0;
        if (!isBelowThreshold) {
            log.log(Level.WARNING, "Block producer is not a winner of the slot");
        }
        return Schnorrkel.getInstance().vrfVerify(new Schnorrkel.PublicKey(verifyingAuthority.getPublicKey()), transcriptData, vrfOutputAndProof) && isBelowThreshold;
    }
}
