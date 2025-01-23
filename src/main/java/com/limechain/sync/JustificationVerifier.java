package com.limechain.sync;

import com.limechain.chain.lightsyncstate.Authority;
import com.limechain.grandpa.state.GrandpaSetState;
import com.limechain.network.protocol.warp.dto.PreCommit;
import com.limechain.rpc.server.AppBean;
import com.limechain.runtime.hostapi.dto.Key;
import com.limechain.runtime.hostapi.dto.VerifySignature;
import com.limechain.utils.Ed25519Utils;
import com.limechain.utils.LittleEndianUtils;
import com.limechain.utils.StringUtils;
import io.emeraldpay.polkaj.types.Hash256;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.java.Log;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;

@Log
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class JustificationVerifier {
    public static boolean verify(PreCommit[] preCommits, BigInteger round) {
        GrandpaSetState grandpaSetState = AppBean.getBean(GrandpaSetState.class);
        Authority[] authorities = grandpaSetState.getAuthorities().toArray(new Authority[0]);
        BigInteger authoritiesSetId = grandpaSetState.getSetId();

        // Implementation from: https://github.com/smol-dot/smoldot
        // lib/src/finality/justification/verify.rs
        if (authorities == null || preCommits.length < (authorities.length * 2 / 3) + 1) {
            log.log(Level.WARNING, "Not enough signatures");
            return false;
        }

        Set<Hash256> seenPublicKeys = new HashSet<>();
        Set<Hash256> authorityKeys = Arrays.stream(authorities)
                .map(Authority::getPublicKey)
                .map(Hash256::new)
                .collect(Collectors.toSet());

        for (PreCommit preCommit : preCommits) {
            if (!authorityKeys.contains(preCommit.getAuthorityPublicKey())) {
                log.log(Level.WARNING, "Invalid Authority for preCommit");
                return false;
            }

            if (seenPublicKeys.contains(preCommit.getAuthorityPublicKey())) {
                log.log(Level.WARNING, "Duplicated signature");
                return false;
            }
            seenPublicKeys.add(preCommit.getAuthorityPublicKey());

            // TODO (from smoldot): must check signed block ancestry using `votes_ancestries`

            byte[] data = getDataToVerify(preCommit, authoritiesSetId, round);

            boolean isValid = verifySignature(preCommit.getAuthorityPublicKey().toString(),
                    preCommit.getSignature().toString(), data);
            if (!isValid) {
                log.log(Level.WARNING, "Failed to verify signature");
                return false;
            }
        }
        log.log(Level.INFO, "All signatures were verified successfully");

        // From Smoldot implementation:
        // TODO: must check that votes_ancestries doesn't contain any unused entry
        // TODO: there's also a "ghost" thing?

        return true;
    }

    private static byte[] getDataToVerify(PreCommit preCommit, BigInteger authoritiesSetId, BigInteger round){
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
                .convertBytes(StringUtils.hexToBytes(preCommit.getTargetHash().toString())));
        //Write Justification round bytes as u64
        messageBuffer.put(LittleEndianUtils
                .bytesToFixedLength(preCommit.getTargetNumber().toByteArray(), 4));
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
