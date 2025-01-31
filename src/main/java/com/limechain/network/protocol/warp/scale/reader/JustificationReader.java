package com.limechain.network.protocol.warp.scale.reader;

import com.limechain.grandpa.vote.SignedVote;
import com.limechain.network.protocol.grandpa.messages.catchup.res.SignedVoteScaleReader;
import com.limechain.network.protocol.warp.dto.BlockHeader;
import com.limechain.network.protocol.warp.dto.Justification;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.scale.ScaleReader;
import io.emeraldpay.polkaj.scale.reader.UInt64Reader;
import io.emeraldpay.polkaj.types.Hash256;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class JustificationReader implements ScaleReader<Justification> {

    private static final JustificationReader INSTANCE = new JustificationReader();

    public static JustificationReader getInstance() {
        return INSTANCE;
    }

    @Override
    public Justification read(ScaleCodecReader reader) {
        Justification justification = new Justification();
        justification.setRoundNumber(new UInt64Reader().read(reader));

        // Target hash and target block constitute the "GRANDPA Vote":
        // https://spec.polkadot.network/sect-finality#defn-vote
        justification.setTargetHash(new Hash256(reader.readUint256()));
        justification.setTargetBlock(BlockNumberReader.getInstance().read(reader));

        int signedVotesCount = reader.readCompactInt();
        SignedVote[] signedVotes = new SignedVote[signedVotesCount];
        SignedVoteScaleReader signedVoteReader = SignedVoteScaleReader.getInstance();
        for (int i = 0; i < signedVotesCount; i++) {
            signedVotes[i] = signedVoteReader.read(reader);
        }
        justification.setSignedVotes(signedVotes);

        int ancestryCount = reader.readCompactInt();
        BlockHeader[] ancestries = new BlockHeader[ancestryCount];
        BlockHeaderReader blockHeaderReader = BlockHeaderReader.getInstance();
        for (int i = 0; i < ancestryCount; i++) {
            ancestries[i] = blockHeaderReader.read(reader);
        }
        justification.setAncestryVotes(ancestries);

        return justification;
    }
}
