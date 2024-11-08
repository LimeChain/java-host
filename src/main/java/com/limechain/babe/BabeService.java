package com.limechain.babe;

import com.limechain.babe.predigest.BabePreDigest;
import com.limechain.babe.state.EpochState;
import com.limechain.storage.crypto.KeyStore;
import org.apache.commons.collections4.map.HashedMap;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.Map;

@Component
public class BabeService {

    private final EpochState epochState;
    private final KeyStore keyStore;
    private final Map<BigInteger, BabePreDigest> slotToPreRuntimeDigest = new HashedMap<>();

    public BabeService(EpochState epochState, KeyStore keyStore) {
        this.epochState = epochState;
        this.keyStore = keyStore;
    }

    //TODO: Remove
    public void onSlotChange() {
        System.out.println("Slot is changed");
    }

    //TODO: Remove
    public void onEpochChange() {
        System.out.println("Epoch is changed");
    }

    public void executeEpochLottery() {
        var epochStartSlotNumber = epochState.getCurrentEpochStartSlotNumer();
        var epochEndSlotNumber = epochStartSlotNumber.add(epochState.getEpochLength());

        for (BigInteger slot = epochStartSlotNumber; slot.compareTo(epochEndSlotNumber) < 0; slot = slot.add(BigInteger.ONE)) {
            BabePreDigest babePreDigest = Authorship.claimSlot(epochState, slot, keyStore);
            if (babePreDigest != null) {
                slotToPreRuntimeDigest.put(slot, babePreDigest);
            }
        }
    }
}
