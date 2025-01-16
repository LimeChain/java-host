package com.limechain.babe.state;

import com.limechain.ServiceConsensusState;
import com.limechain.babe.api.BabeApiConfiguration;
import com.limechain.babe.consensus.BabeConsensusMessage;
import com.limechain.runtime.Runtime;
import com.limechain.state.AbstractState;
import com.limechain.storage.KVRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.time.Instant;

/**
 * Represents the state information for the current/next epoch that is needed
 * for block production with BABE.
 * Note: Intended for use only when the host is configured as an Authoring Node.
 */
@Getter
@Component
@RequiredArgsConstructor
public class EpochState extends AbstractState implements ServiceConsensusState {

    private final KVRepository<String, Object> repository;

    private long disabledAuthority;
    private BigInteger slotDuration;
    private BigInteger epochLength;
    private BigInteger genesisSlotNumber;

    private EpochData currentEpochData;
    private EpochDescriptor currentEpochDescriptor;

    private EpochData nextEpochData;
    private EpochDescriptor nextEpochDescriptor;

    @Override
    public void initializeFromRuntime(Runtime runtime) {
        populateDataFromRuntime(runtime.getBabeApiConfiguration());
        setGenesisSlotNumber(runtime.getGenesisSlotNumber());
    }

    @Override
    public void initializeFromDatabase() {
        //TODO: Add methods to load epoch state data.
    }

    @Override
    public void persistState() {
        //TODO: Add methods to store epoch state data.
    }

    public void populateDataFromRuntime(BabeApiConfiguration babeApiConfiguration) {
        this.slotDuration = babeApiConfiguration.getSlotDuration();
        this.epochLength = babeApiConfiguration.getEpochLength();
        this.currentEpochData = new EpochData(
                babeApiConfiguration.getAuthorities(), babeApiConfiguration.getRandomness());
        this.currentEpochDescriptor = new EpochDescriptor(
                babeApiConfiguration.getConstant(), babeApiConfiguration.getAllowedSlots());
    }

    public void updateNextEpochConfig(BabeConsensusMessage message) {
        switch (message.getFormat()) {
            case NEXT_EPOCH_DATA -> this.nextEpochData = message.getNextEpochData();
            case DISABLED_AUTHORITY -> this.disabledAuthority = message.getDisabledAuthority();
            case NEXT_EPOCH_DESCRIPTOR -> this.nextEpochDescriptor = message.getNextEpochDescriptor();
        }
    }

    public void switchEpoch() {
        currentEpochData = nextEpochData;
        currentEpochDescriptor = nextEpochDescriptor;
    }

    public void setGenesisSlotNumber(BigInteger retrievedGenesisSlotNumber) {
        if (retrievedGenesisSlotNumber != null) {
            this.genesisSlotNumber = retrievedGenesisSlotNumber;
        } else {
            // If retrieved genesis slot number is null, then there are no executed
            // blocks on the chain and current slot number should be the genesis
            this.genesisSlotNumber = getCurrentSlotNumber();
        }
    }

    public BigInteger getCurrentSlotNumber() {
        return BigInteger.valueOf(Instant.now().toEpochMilli()).divide(slotDuration);
    }

    public Instant getSlotStartTime(BigInteger slotNumber) {
        return Instant.ofEpochMilli(slotNumber.multiply(slotDuration).longValue());
    }

    // (currentSlotNumber - genesisSlotNumber) / epochLength = epochIndex
    // Dividing BigIntegers results in rounding down when the result is not a whole number,
    // which is the intended behavior for calculating epochIndex.
    public BigInteger getCurrentEpochIndex() {
        return getCurrentSlotNumber().subtract(genesisSlotNumber).divide(epochLength);
    }

    // epochIndex * epochLength + genesisSlot = epochStartSlotNumber
    // The formula is the same as below but different variable is isolated, and we
    // leverage the fact that epochStartSlotNumber is achieved when
    // (currentSlotNumber - genesisSlotNumber) / epochLength results in whole number
    public BigInteger getCurrentEpochStartSlotNumber() {
        return getCurrentEpochIndex().multiply(epochLength).add(genesisSlotNumber);
    }

    public BigInteger getEpochStartSlotNumber(BigInteger epochIndex) {
        return epochIndex.multiply(epochLength).add(genesisSlotNumber);
    }

    // Don't use this method for places where range between first and last slot of epoch
    // is needed. It better to use getCurrentEpochStartSlotNumber and manually add epochLength
    // in order to achieve currentEpochEndSlotNumber, but be careful, as the range should be
    // inclusive on the lower bound and exclusive on the upper bound: [..). The described flow
    // is preferable because calling the methods from the epoch state may result in calculations
    // made in two different epochs.
    public BigInteger getCurrentEpochEndSlotNumber() {
        return getCurrentEpochStartSlotNumber().add(epochLength).subtract(BigInteger.ONE);
    }
}
