package com.limechain.grandpa.round;

import com.limechain.exception.grandpa.GrandpaGenericException;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// We should add new GrandpaRound objects to this structure if
// A) We receive new vote for round which is greater with 1 than the newest round that we have in this queue
// B) When we start new Play-Grandpa

// When we finalize a round we should delete all previous GrandpaRounds
@Component
public class RoundCache {

    private static final int CACHE_SET_CAPACITY = 4;
    private final Map<BigInteger, List<GrandpaRound>> roundMap = Collections.synchronizedMap(new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<BigInteger, List<GrandpaRound>> eldest) {
            return size() > CACHE_SET_CAPACITY;
        }
    });

    public synchronized void addRound(BigInteger setId, GrandpaRound grandpaRound) {
        roundMap.putIfAbsent(setId, new ArrayList<>());

        List<GrandpaRound> rounds = roundMap.get(setId);
        GrandpaRound currentRound = rounds.isEmpty() ? null : rounds.getLast();

        if (currentRound != null) {
            BigInteger expectedRoundNumber = currentRound.getRoundNumber().add(BigInteger.ONE);
            if (!expectedRoundNumber.equals(grandpaRound.getRoundNumber())) {
                throw new GrandpaGenericException("Next round number isn't equal to the current round number plus one");
            }

//            grandpaRound.setPrevious(currentRound);
        }

        rounds.add(grandpaRound);
    }

    public synchronized void removeRoundOlderThan(BigInteger setId, BigInteger lastFinalizedRoundNumber) {
        List<GrandpaRound> rounds = roundMap.get(setId);
        if (rounds == null) {
            return;
        }

//        BigInteger roundNumberBeforeLastFinalizedRound = lastFinalizedRoundNumber.subtract(BigInteger.ONE);
//        rounds.stream()
//                .filter(r -> r.getRoundNumber().equals(roundNumberBeforeLastFinalizedRound))
//                .findFirst().ifPresent(roundBeforeFinalized -> roundBeforeFinalized.setPrevious(null));

        // Remove all GrandpaRound objects with smaller numbers than the last finalized round number
        rounds.removeIf(r -> r.getRoundNumber().compareTo(lastFinalizedRoundNumber) < 0);
    }

    public synchronized GrandpaRound getRound(BigInteger setId, BigInteger roundNumber) {
        List<GrandpaRound> rounds = roundMap.get(setId);
        if (rounds == null) {
            return null;
        }

        return rounds.stream()
                .filter(r -> r.getRoundNumber().equals(roundNumber))
                .findFirst()
                .orElse(null);
    }

    public synchronized GrandpaRound getLatestRound(BigInteger setId) {
        List<GrandpaRound> rounds = roundMap.get(setId);
        if (rounds == null || rounds.isEmpty()) {
            return null;
        }
        return rounds.getLast();
    }

    public synchronized BigInteger getLatestRoundNumber(BigInteger setId) {
        GrandpaRound latestRound = getLatestRound(setId);
        return latestRound != null ? latestRound.getRoundNumber() : null;
    }
}
