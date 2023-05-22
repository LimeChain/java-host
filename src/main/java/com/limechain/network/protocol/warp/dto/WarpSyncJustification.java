package com.limechain.network.protocol.warp.dto;

import com.limechain.chain.lightsyncstate.Authority;
import io.emeraldpay.polkaj.scaletypes.Extrinsic;
import io.emeraldpay.polkaj.types.Hash256;
import io.libp2p.crypto.keys.Ed25519PublicKey;
import lombok.Setter;
import lombok.extern.java.Log;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;

@Setter
@Log
public class WarpSyncJustification {
    public BigInteger round;
    public Hash256 targetHash;
    public BigInteger targetBlock;
    public Precommit[] precommits;
    public BlockHeader[] ancestryVotes;

    @Override
    public String toString() {
        return "WarpSyncJustification{" +
                "round=" + round +
                ", targetHash=" + targetHash +
                ", targetBlock=" + targetBlock +
                ", precommits=" + Arrays.toString(precommits) +
                ", ancestryVotes=" + Arrays.toString(ancestryVotes) +
                '}';
    }

    public boolean verify(Authority[] authorities, BigInteger authoritiesSetId) {
        // TODO: implement https://github.com/smol-dot/smoldot/blob/165412f0292009aedd208615a37cf2859fd45936/lib/src/finality/justification/verify.rs#L50
        if (precommits.length < authorities.length * 2 / 3 + 1) {
            log.log(Level.WARNING, "Not enough signatures");
            return false;
        }

        Set<Hash256> seen_pub_keys = new HashSet<>();
        Set<Hash256> authorityKeys = Arrays.stream(authorities).map(Authority::getPublicKey).collect(Collectors.toSet());

        for (Precommit precommit : precommits) {
            if (!authorityKeys.contains(precommit.getAuthorityPublicKey())) {
                log.log(Level.WARNING, "Invalid Authority for precommit");
                return false;
            }

            if (seen_pub_keys.contains(precommit.getAuthorityPublicKey())) {
                log.log(Level.WARNING, "Duplicated signature");
                return false;
            }
            seen_pub_keys.add(precommit.getAuthorityPublicKey());

            // TODO (from smoldot): must check signed block ancestry using `votes_ancestries`

            // 1 reserved byte for data type
            // 32 reserved for target hash
            // 4 reserved for block number
            // 8 reserved for justification round
            // 8 reserved for set id
            List<Byte> message = new ArrayList<>();
            int messageCapacity = 1 + 32 + 4 + 8 + 8;
            // Write message type
            message.add((byte) 1);

            // Write target hash
            byte[] targetHash = precommit.getTargetHash().getBytes();
            for (byte targetHashByte : targetHash) {
                message.add(targetHashByte);
            }

            byte[] targetNumberBytes = precommit.getTargetNumber().toByteArray();
            int blockNumberBytes = 4;
            int targetNumberSize = Math.min(targetNumberBytes.length, blockNumberBytes);

            //Write Justification round bytes as an u64 and fill out missing zero bytes
            for (int i = targetNumberSize; i < blockNumberBytes; i++) {
                message.add((byte) 0);
            }
            for (int i = 0; i < targetNumberSize; i++) {
                message.add(targetNumberBytes[i]);
            }

            //Write Justification round bytes as an u64 and fill out missing zero bytes
            byte[] justificationRoundBytes = this.round.toByteArray();
            for (int i = justificationRoundBytes.length; i < 8; i++) {
                message.add((byte) 0);
            }
            for (int i = 0; i < justificationRoundBytes.length; i++) {
                message.add(justificationRoundBytes[i]);
            }

            //Write Set Id bytes as a u64 and fill out missing zero bytes
            byte[] setIdBytes = authoritiesSetId.toByteArray();
            for (int i = setIdBytes.length; i < 8; i++) {
                message.add((byte) 0);
            }
            for (int i = 0; i < setIdBytes.length; i++) {
                message.add(setIdBytes[i]);
            }

            if (message.size() != messageCapacity) {
                log.log(Level.WARNING, "Message size not equal to expected capacity");
                return false;
            }

            //Verify message
            //Might have problems because we use the stand ED25519 instead of ED25519_zebra
            Ed25519PublicKey publicKey =
                    new Ed25519PublicKey(new Ed25519PublicKeyParameters(precommit.getAuthorityPublicKey().getBytes()));
            Extrinsic.ED25519Signature signature = new Extrinsic.ED25519Signature(precommit.getSignature());

            byte[] data = new byte[messageCapacity];
            for (int i = 0; i < message.size(); i++) {
                data[i] = message.get(i);
            }

            boolean result = publicKey.verify(data, signature.getValue().getBytes());
            if (!result) {
                log.log(Level.WARNING, "Invalid signature");
                //return false;
            }
        }
        // From Smoldot implementation:
        // TODO: must check that votes_ancestries doesn't contain any unused entry
        // TODO: there's also a "ghost" thing?

        return true;
    }
}
