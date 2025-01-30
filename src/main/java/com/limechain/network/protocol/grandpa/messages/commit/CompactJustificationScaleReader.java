package com.limechain.network.protocol.grandpa.messages.commit;

import com.limechain.exception.network.SignatureCountMismatchException;
import com.limechain.grandpa.vote.Vote;
import com.limechain.network.protocol.warp.dto.PreCommit;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.scale.ScaleReader;
import io.emeraldpay.polkaj.scale.reader.ListReader;
import io.emeraldpay.polkaj.types.Hash256;
import io.emeraldpay.polkaj.types.Hash512;

import java.util.List;

public class CompactJustificationScaleReader implements ScaleReader<PreCommit[]> {

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
    public PreCommit[] read(ScaleCodecReader reader) {
        List<Vote> votes = voteListReader.read(reader);

        int preCommitsCount = votes.size();
        PreCommit[] preCommits = new PreCommit[preCommitsCount];

        for (int i = 0; i < preCommitsCount; i++) {
            Vote vote = votes.get(i);
            preCommits[i] = new PreCommit();
            preCommits[i].setTargetHash(vote.getBlockHash());
            preCommits[i].setTargetNumber(vote.getBlockNumber());
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
