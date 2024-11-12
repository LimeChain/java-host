package com.limechain.babe;

import com.limechain.babe.coordinator.SlotChangeEvent;
import com.limechain.babe.coordinator.SlotChangeListener;
import com.limechain.babe.predigest.BabePreDigest;
import com.limechain.babe.state.EpochState;
import com.limechain.storage.crypto.KeyStore;
import org.apache.commons.collections4.map.HashedMap;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.Map;

@Component
public class BabeService implements SlotChangeListener {

    private final EpochState epochState;
    private final KeyStore keyStore;
    private final Map<BigInteger, BabePreDigest> slotToPreRuntimeDigest = new HashedMap<>();

    public BabeService(EpochState epochState, KeyStore keyStore) {
        this.epochState = epochState;
        this.keyStore = keyStore;
    }

    private void executeEpochLottery(BigInteger epochIndex) {
        var epochStartSlotNumber = epochState.getEpochStartSlotNumber(epochIndex);
        var epochEndSlotNumber = epochStartSlotNumber.add(epochState.getEpochLength());

        for (BigInteger slot = epochStartSlotNumber; slot.compareTo(epochEndSlotNumber) < 0; slot = slot.add(BigInteger.ONE)) {
            BabePreDigest babePreDigest = Authorship.claimSlot(epochState, slot, keyStore);
            if (babePreDigest != null) {
                slotToPreRuntimeDigest.put(slot, babePreDigest);
            }
        }
    }

    @Override
    public void slotChanged(SlotChangeEvent event) {
        // TODO: Add implementation for building a block on every slot change
        EpochSlot slot = event.getEpochSlot();

        if (event.isLastSlotFromCurrentEpoch()) {
            BigInteger nextEpochIndex = slot.getEpochIndex().add(BigInteger.ONE);
            executeEpochLottery(nextEpochIndex);
        }
    }
}
