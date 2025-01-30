package com.limechain.network.protocol.grandpa.messages.commit;

import com.limechain.exception.network.SignatureCountMismatchException;
import com.limechain.network.protocol.grandpa.messages.catchup.res.SignedVote;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.scale.ScaleReader;
import io.emeraldpay.polkaj.scale.reader.ListReader;
import io.emeraldpay.polkaj.types.Hash256;
import io.emeraldpay.polkaj.types.Hash512;

import java.util.List;

public class CompactJustificationScaleReader implements ScaleReader<SignedVote[]> {

    private static final CompactJustificationScaleReader INSTANCE = new CompactJustificationScaleReader();

    private final ListReader<Vote> voteListReader;

    private CompactJustificationScaleReader() {
        VoteScaleReader voteScaleReader = VoteScaleReader.getInstance();
        voteListReader = new ListReader<>(voteScaleReader);
    }

    public static CompactJustificationScaleReader getInstance() {
        return INSTANCE;
    }

    @Override
    public SignedVote[] read(ScaleCodecReader reader) {
        List<Vote> votes = voteListReader.read(reader);

        int preCommitsCount = votes.size();
        SignedVote[] preCommits = new SignedVote[preCommitsCount];

        for (int i = 0; i < preCommitsCount; i++) {
            preCommits[i] = new SignedVote();
            preCommits[i].setVote(votes.get(i));
        }

        int signaturesCount = reader.readCompactInt();
        if (signaturesCount != preCommitsCount) {
            throw new SignatureCountMismatchException(
                    String.format("Number of signatures (%d) does not match number of preCommits (%d)",
                            signaturesCount, preCommitsCount));
        }

        for (int i = 0; i < signaturesCount; i++) {
            preCommits[i].setSignature(new Hash512(reader.readByteArray(64)));
            preCommits[i].setAuthorityPublicKey(new Hash256(reader.readUint256()));
        }

        return preCommits;
    }
}
