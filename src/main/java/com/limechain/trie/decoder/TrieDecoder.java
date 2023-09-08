package com.limechain.trie.decoder;

import com.limechain.trie.Node;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import lombok.experimental.UtilityClass;

import static com.limechain.trie.decoder.TrieHeaderDecoder.decodeHeader;

//TODO: Are all SCALE readers wrapped in try-catch???
@UtilityClass
public class TrieDecoder {
    /**
     * Decodes encoded node data and its children recursively from a byte array.
     *
     * @param encoded a byte array containing the encoded node data
     * @return the decoded Node object
     * @throws TrieDecoderException if the variant does not match known variants
     */
    public static Node decode(byte[] encoded) {
        ScaleCodecReader reader = new ScaleCodecReader(encoded);
        TrieHeaderDecoderResult header = decodeHeader(reader);
        switch (header.nodeVariant()) {
            case EMPTY -> {
                return null;
            }
            case LEAF, LEAF_WITH_HASHED_VALUE -> {
                return TrieLeafDecoder.decode(reader, header.nodeVariant(), header.partialKeyLengthHeader());
            }
            case BRANCH, BRANCH_WITH_VALUE, BRANCH_WITH_HASHED_VALUE -> {
                return TrieBranchDecoder.decode(reader, header.nodeVariant(), header.partialKeyLengthHeader());
            }
            default -> throw new TrieDecoderException("Unknown variant: " + header.nodeVariant());
        }
    }
}
