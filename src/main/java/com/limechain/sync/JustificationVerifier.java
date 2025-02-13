package com.limechain.sync;

import com.limechain.chain.lightsyncstate.Authority;
import com.limechain.grandpa.state.GrandpaSetState;
import com.limechain.grandpa.vote.SignedVote;
import com.limechain.grandpa.vote.Vote;
import com.limechain.network.protocol.warp.dto.Justification;
import com.limechain.rpc.server.AppBean;
import com.limechain.runtime.hostapi.dto.Key;
import com.limechain.runtime.hostapi.dto.VerifySignature;
import com.limechain.storage.block.state.BlockState;
import com.limechain.utils.Ed25519Utils;
import com.limechain.utils.LittleEndianUtils;
import com.limechain.utils.StringUtils;
import io.emeraldpay.polkaj.types.Hash256;
import lombok.experimental.UtilityClass;
import lombok.extern.java.Log;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.stream.Collectors;

@Log
@UtilityClass
public class JustificationVerifier {

    public static boolean verify(Justification justification) {

        if (justification == null) {
            log.log(Level.WARNING, "Empty justification provided for verification");
            return false;
        }

        GrandpaSetState grandpaSetState = AppBean.getBean(GrandpaSetState.class);
        BlockState blockState = AppBean.getBean(BlockState.class);

        List<Authority> authorities = grandpaSetState.getAuthorities();
        BigInteger threshold = grandpaSetState.getThreshold(authorities);

        // Implementation from: https://github.com/smol-dot/smoldot
        // lib/src/finality/justification/verify.rs
        if (BigInteger.valueOf(justification.getSignedVotes().length).compareTo(threshold) < 0) {
            log.log(Level.WARNING, "Not enough signatures");
            return false;
        }

        BigInteger setId = grandpaSetState.getSetId();
        Set<Hash256> authorityKeys = authorities.stream()
                .map(Authority::getPublicKey)
                .map(Hash256::new)
                .collect(Collectors.toSet());

        if (!groupAndValidateAuthorityVotes(blockState, justification, setId, authorityKeys)) {
            return false;
        }

        if (justification.getAncestryVotes() != null &&
                Arrays.stream(justification.getAncestryVotes())
                        .anyMatch(vote -> !blockState.isDescendantOf(justification.getTargetHash(), vote.getHash()))
        ) {
            log.log(Level.WARNING, "Ancestry vote block is not a descendant of the target block");
            return false;
        }

        log.log(Level.INFO, "All signatures were verified successfully");

        return true;
    }

    private boolean groupAndValidateAuthorityVotes(BlockState blockState,
                                                   Justification justification,
                                                   BigInteger setId,
                                                   Set<Hash256> authorityKeys) {

        return Arrays.stream(justification.getSignedVotes())
                .collect(Collectors.groupingBy(SignedVote::getAuthorityPublicKey))
                .entrySet().stream()
                .allMatch(
                        validateAllVotesFromSingleAuthority(
                                blockState,
                                justification.getTargetHash(),
                                setId,
                                justification.getRoundNumber(),
                                authorityKeys
                        )
                );
    }

    private static Predicate<Map.Entry<Hash256, List<SignedVote>>> validateAllVotesFromSingleAuthority(
            BlockState blockState,
            Hash256 targetBlockHash,
            BigInteger setId,
            BigInteger roundNumber,
            Set<Hash256> authorityKeys) {

        return entry -> {

            Hash256 authorityKey = entry.getKey();
            List<SignedVote> signedVotes = entry.getValue();

            if (signedVotes.size() > 3) {
                log.log(Level.WARNING, "Authority submitted more than 1 valid vote and 2 equivocatory votes");
                return false;
            }

            if (!authorityKeys.contains(authorityKey)) {
                log.log(Level.WARNING, "Invalid Authority for vote");
                return false;
            }

            for (SignedVote signedVote : signedVotes) {

                byte[] data = getDataToVerify(
                        signedVote.getVote(),
                        setId,
                        roundNumber
                );

                boolean validSignature = verifySignature(
                        authorityKey.toString(),
                        signedVote.getSignature().toString(),
                        data
                );

                if (!validSignature) {
                    log.log(Level.WARNING, "Failed to verify signature");
                    return false;
                }

                if (!blockState.isDescendantOf(targetBlockHash, signedVote.getVote().getBlockHash())) {
                    log.log(Level.WARNING, "Vote block is not a descendant of the target block");
                    return false;
                }
            }

            return true;
        };
    }

    private static byte[] getDataToVerify(Vote vote, BigInteger authoritiesSetId, BigInteger round) {
        // 1 reserved byte for data type
        // 32 reserved for target hash
        // 4 reserved for block number
        // 8 reserved for justification round
        // 8 reserved for set id
        int messageCapacity = 1 + 32 + 4 + 8 + 8;
        var messageBuffer = ByteBuffer.allocate(messageCapacity);
        messageBuffer.order(ByteOrder.LITTLE_ENDIAN);

        // Write message type
        messageBuffer.put((byte) 1);
        // Write target hash
        messageBuffer.put(LittleEndianUtils
                .convertBytes(StringUtils.hexToBytes(vote.getBlockHash().toString())));
        //Write Justification round bytes as u64
        messageBuffer.put(LittleEndianUtils
                .bytesToFixedLength(vote.getBlockNumber().toByteArray(), 4));
        //Write Justification round bytes as u64
        messageBuffer.put(LittleEndianUtils.bytesToFixedLength(round.toByteArray(), 8));
        //Write Set Id bytes as u64
        messageBuffer.put(LittleEndianUtils.bytesToFixedLength(authoritiesSetId.toByteArray(), 8));

        //Verify message
        //Might have problems because we use the stand ED25519 instead of ED25519_zebra
        messageBuffer.rewind();
        byte[] data = new byte[messageBuffer.remaining()];
        messageBuffer.get(data);
        return data;
    }

    public static boolean verifySignature(String publicKeyHex, String signatureHex, byte[] data) {
        byte[] publicKeyBytes = StringUtils.hexToBytes(publicKeyHex);
        byte[] signatureBytes = StringUtils.hexToBytes(signatureHex);

        return Ed25519Utils.verifySignature(new VerifySignature(signatureBytes, data, publicKeyBytes, Key.ED25519));
    }
}
