package com.limechain.trie.dto.node;

import com.limechain.exception.trie.NodeDecodingException;
import com.limechain.exception.trie.NodeEncodingException;
import com.limechain.trie.decoded.NodeVariant;
import com.limechain.trie.structure.nibble.BytesToNibbles;
import com.limechain.trie.structure.nibble.Nibble;
import com.limechain.trie.structure.nibble.Nibbles;
import com.limechain.trie.structure.nibble.NibblesUtils;
import com.limechain.utils.scale.ScaleUtils;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.scale.ScaleCodecWriter;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;
import java.util.stream.Stream;

// NOTE:
//  This `extends` restriction on the generic type is only used for `.size()` methods
//  These needn't be restrictions on the node itself, but rather only for the `encode` method
//  Which could alternatively be implemented as a generic static methods only for nodes obeying these constraints
//  But we don't need that much overcomplicating now.
@Getter
@AllArgsConstructor
public class DecodedNode<C extends Collection<Byte>> {
    public static final int CHILDREN_COUNT = 16;

    private static final int HASHED_STORAGE_VALUE_LENGTH = 32;
    private static final int CHILD_BYTES_SIZE_LIMIT = 32;

    /**
     * Non-null list of nullable entries (null in the list indicating there's no child).
     */
    private List<C> children;

    private Nibbles partialKey;

    @Nullable
    private StorageValue storageValue;

    public boolean hasChildren() {
        return Arrays.stream(this.children.toArray()).anyMatch(Objects::nonNull);
    }

    public int getChildrenBitmap() {
        return IntStream.range(0, CHILDREN_COUNT)
            .filter(i -> Objects.nonNull(this.children.get(i)))
            .reduce(0, (bitmap, i) -> bitmap | (1 << i));
    }

    /**
     * Calculates the NodeVariant of this node depending on whether:
     * - it has children (i.e. branch / leaf node)
     * - it has a storage value
     * - this storage value (if present) is hashed
     *
     * @return the NodeVariant of this DecodedNode
     */
    public NodeVariant calculateNodeVariant() {
        return this.hasChildren() ? calculateBranchNodeVariant() : calculateLeafNodeVariant();
    }

    private NodeVariant calculateLeafNodeVariant() {
        if (this.storageValue == null) {
            if (!this.partialKey.isEmpty()) {
                throw new NodeEncodingException("Trie node has a partial key, but no children and no storage value.");
            }

            return NodeVariant.EMPTY;
        }

        return this.storageValue.isHashed() ? NodeVariant.LEAF_WITH_HASHED_VALUE : NodeVariant.LEAF;
    }

    private NodeVariant calculateBranchNodeVariant() {
        if (this.storageValue == null) {
            return NodeVariant.BRANCH;
        }

        return this.storageValue.isHashed() ? NodeVariant.BRANCH_WITH_HASHED_VALUE : NodeVariant.BRANCH_WITH_VALUE;
    }

    private List<Byte> encodeNodeHeader() {
        List<Byte> headerEncoding = new ArrayList<>(2 + (this.partialKey.size() / 255));

        // Calculate the first byte
        NodeVariant variant = this.calculateNodeVariant();
        int maxRepresentableInFirstByte = variant.getPartialKeyLengthHeaderMask();
        byte firstByte = (byte) (variant.bits | Math.min(this.partialKey.size(),
            maxRepresentableInFirstByte)); // a byte cast effectively trims to the last 8 bits, exactly what we want

        headerEncoding.add(firstByte);

        // Append as many "private key length" bytes as necessary
        int pkLen = this.partialKey.size();
        if (pkLen >= maxRepresentableInFirstByte) {
            int remainingPkLen = pkLen - maxRepresentableInFirstByte;
            int numberOfFullBytes = remainingPkLen / 255;
            int lastByte = remainingPkLen % 255;

            for (int i = 0; i < numberOfFullBytes; ++i) {
                headerEncoding.add((byte) 255);
            }
            headerEncoding.add((byte) lastByte);
        }

        return headerEncoding;
    }

    private List<Byte> encodePartialKey() {
        return NibblesUtils.toBytesPrepending(this.partialKey);
    }

    // TODO: Optimize, a lot of unnecessary copying is going on.
    private List<Byte> encodeSubvalue() {
        List<Byte> subvalue = new LinkedList<>(); //NOTE: Maybe Arraylist?

        // First, push the children bitmap (if a branch node)
        if (this.hasChildren()) {
            //TODO:
            // This doesn't conform to the spec exactly, but that's how smoldot and gossamer do it
            // Why do we reverse the bitmap to LittleEndian when the spec explicitly defines it as a sequence of BITs?
            // https://spec.polkadot.network/chap-state#defn-node-subvalue
            int childrenBitmap = this.getChildrenBitmap();
            subvalue.add((byte) (childrenBitmap & 0x00FF));
            subvalue.add((byte) ((childrenBitmap >> 8) & 0x00FF));
        }

        // Then, encode the storage value
        if (this.storageValue != null) {
            // If the storage value is not hashed, we must also include its byte length in the scale encoding
            // NOTE:
            //  Why do we only add length to the encoding if the value is not hashed?
            //  I presume, because if it's hashed, we know it's 32 bytes only and don't need the length information?
            //  And we know whether it's hashed from the header.
            if (!this.storageValue.isHashed()) {
                for (byte b : ScaleUtils.Encode.encode(ScaleCodecWriter::writeCompact, this.storageValue.value().length)) {
                    subvalue.add(b);
                }
            }

            for (byte b : this.storageValue.value()) {
                subvalue.add(b);
            }
        }

        // And finally, the children node values
        List<Byte> childrenNodeValues =
            this.children.stream()
                .filter(Objects::nonNull)
                .flatMap(childValue -> {
                    byte[] scaleEncodedChildValue = ScaleUtils.Encode.encodeAsListOfBytes(childValue);
                    return Stream.of(ArrayUtils.toObject(scaleEncodedChildValue));
                })
                .toList();
        subvalue.addAll(childrenNodeValues);

        // Return everything accumulated thus far
        return subvalue;
    }

    /**
     * Encodes the components of a node value into the node value itself.
     * <br>
     * This function returns an iterator of buffers. The actual node value is the concatenation of
     * these buffers put together.
     * <br>
     * > <b>Note</b>:
     * The returned iterator might contain a reference to the storage value and children
     * values in the [`DecodedNode`]. By returning an iterator of buffers, we avoid copying
     * these storage value and children values.
     * <br>
     * This encoding is independent of the trie version.
     *
     * @return The return value is composed of three parts:<br>
     * - node header,<br>
     * - the partial key,<br>
     * - the node subvalue.
     * @throws NodeEncodingException if the node represents invalid state;
     *                               for now only if it has a partial key, but no children and no storage value
     */
    public List<Byte> encode() {
        return Stream.of(
                this.encodeNodeHeader(),
                this.encodePartialKey(),
                this.encodeSubvalue()
            )
            .flatMap(Collection::stream)
            .toList();
    }

    /**
     * Calculates the Merkle value of the given node.
     * Ultimately, almost the same as {@link DecodedNode#encode()}, except that the encoding is then optionally hashed.
     * Hashing is performed if the encoded value is 32 bytes or more, or if isRootNode is true.
     * This is the reason why {@code isRootNode} must be provided.
     *
     * @param hashFunction the hashing function
     * @param isRootNode   must be true if the encoded node is the root node of the trie.
     * @return the merkle value of the node
     */
    // NOTE:
    //  Passing the hashFunction as a lambda might be insufficient for future use cases, but it's enough for now
    //  Feel free to refactor if needed.
    public byte[] calculateMerkleValue(UnaryOperator<byte[]> hashFunction, boolean isRootNode) {
        byte[] nodeValue = ArrayUtils.toPrimitive(this.encode().toArray(Byte[]::new));

        // The node value must be hashed if we're the root or otherwise, if it exceeds 31 bytes of length
        if (isRootNode || nodeValue.length >= 32) {
            nodeValue = hashFunction.apply(nodeValue);
        }

        return nodeValue;
    }

    /**
     * Decodes a node value found in a proof into its components.
     * This can decode nodes no matter their version or hash algorithm.
     *
     * @param encoded The byte array representing the encoded node.
     * @return {@code DecodedNode<Nibbles, List <Byte>>} The decoded node
     * @throws NodeDecodingException    If the encoded array is null, empty, or invalid.
     * @throws IllegalArgumentException If 'encoded' is null.
     */
    public static DecodedNode<List<Byte>> decode(byte[] encoded) {
        ScaleCodecReader reader = new ScaleCodecReader(encoded);
        if (encoded == null || encoded.length == 0) {
            throw new NodeDecodingException("Invalid node value: it's empty");
        }

        int firstByte = reader.readByte() & 0xFF;
        boolean hasChildren;
        // NOTE: A null value for storageValueHashed implies that there is no storage value for the decoded node
        Boolean storageValueHashed;
        int pkLenFirstByteBits;

        // https://spec.polkadot.network/#defn-node-header
        switch (firstByte >> 6) {
            case 0b00:
                if ((firstByte >> 5) == 0b001) {
                    hasChildren = false;
                    storageValueHashed = true;
                    pkLenFirstByteBits = 5;
                } else if ((firstByte >> 4) == 0b0001) {
                    hasChildren = true;
                    storageValueHashed = true;
                    pkLenFirstByteBits = 4;
                } else if (firstByte == 0) {
                    hasChildren = false;
                    storageValueHashed = null;
                    pkLenFirstByteBits = 6;
                } else {
                    throw new NodeDecodingException("Invalid header bits");
                }
                break;
            case 0b10:
                hasChildren = true;
                storageValueHashed = null;
                pkLenFirstByteBits = 6;
                break;
            case 0b01:
                hasChildren = false;
                storageValueHashed = false;
                pkLenFirstByteBits = 6;
                break;
            case 0b11:
                hasChildren = true;
                storageValueHashed = false;
                pkLenFirstByteBits = 6;
                break;
            default:
                throw new NodeDecodingException("Unknown variant");
        }

        int pkLen = decodePkLen(reader, firstByte, pkLenFirstByteBits);

        if (pkLen != 0 && !hasChildren && storageValueHashed == null) {
            throw new NodeDecodingException("Empty trie with partial key ");
        }

        byte[] partialKeyLEBytes = decodePartialKey(reader, pkLen);

        int childrenBitmap = decodeChildrenBitmap(reader, hasChildren);

        byte[] storageValue = decodeStorageValue(reader, storageValueHashed);

        List<List<Byte>> children = decodeChildren(reader, childrenBitmap);

        Iterator<Nibble> pkNibbles = new BytesToNibbles(partialKeyLEBytes).iterator();

        // This is for situations where the partial key contains a `0` prefix that exists for
        // alignment but doesn't actually represent a nibble. So we skip it.
        if (pkLen % 2 == 1) {
            pkNibbles.next();
        }

        return new DecodedNode<>(
            children,
            Nibbles.of(pkNibbles),
            new StorageValue(storageValue, Objects.requireNonNull(storageValueHashed))
        );
    }

    /**
     * Calculates the length of the partial key in nibbles
     *
     * @param reader             ScaleCodecReader used to read the encoded node data.
     * @param firstByte          The first byte of the encoded node data.
     * @param pkLenFirstByteBits The number of bits in the first byte dedicated to encoding the partial key length.
     * @return int The length of the partial key.
     * @throws NodeDecodingException If there's an error in decoding, such as if the partial key length is too short,
     *                               or if there's an overflow in calculating the partial key length.
     */
    private static int decodePkLen(ScaleCodecReader reader, int firstByte, int pkLenFirstByteBits) {
        int pkLen = firstByte & ((1 << pkLenFirstByteBits) - 1);

        boolean continueIter = pkLen == ((1 << pkLenFirstByteBits) - 1);

        while (continueIter) {
            if (!reader.hasNext()) {
                throw new NodeDecodingException("PartialKeyLenTooShort");
            }
            byte currentByte = reader.readByte();
            continueIter = (currentByte & 0xFF) == 255;
            int addedValue = (currentByte & 0xFF);

            if (pkLen > Integer.MAX_VALUE - addedValue) {
                throw new NodeDecodingException("Partial key length overflow");
            }

            pkLen += addedValue;
        }
        return pkLen;
    }

    /**
     * Extracts the partial key from a node in a trie structure using a ScaleCodecReader.
     *
     * @param reader The ScaleCodecReader used to read the encoded node data.
     * @param pkLen  The length of the partial key in nibbles.
     * @return byte[] The byte array containing the partial key.
     * @throws NodeDecodingException If there are no more bytes to read, or if the partial key has invalid padding.
     */
    private static byte[] decodePartialKey(ScaleCodecReader reader, int pkLen) {
        if (!reader.hasNext()) {
            throw new NodeDecodingException("Node value cannot be null for partial key");
        }

        // Calculate the length of the partial key in bytes
        int pkLenBytes = pkLen == 0 ? 0 : 1 + ((pkLen - 1) / 2);
        byte[] partialKey = reader.readByteArray(pkLenBytes);

        if ((pkLen % 2 == 1) && ((partialKey[0] & 0xf0) != 0)) {
            throw new NodeDecodingException("Invalid Partial key padding");
        }

        return partialKey;
    }

    /**
     * Extracts the children bitmap from a node in a trie structure.
     *
     * @param reader      The ScaleCodecReader used to read the encoded node data.
     * @param hasChildren A boolean indicating whether the node has children.
     * @return int The children bitmap as an integer. If the node has no children, returns 0.
     * @throws NodeDecodingException If there are no more bytes to read, or if the children bitmap is zero.
     */
    private static int decodeChildrenBitmap(ScaleCodecReader reader, boolean hasChildren) {
        if (!reader.hasNext()) {
            throw new NodeDecodingException("Node value cannot be null for children bitmap");
        }

        if (!hasChildren) {
            return 0;
        }
        byte[] bitmap;
        final int BITMAP_DATA_LENGTH = 2;
        bitmap = reader.readByteArray(BITMAP_DATA_LENGTH);
        int childrenBitmap = ((bitmap[0] & 0xFF) | (bitmap[1] & 0xFF) << 8);

        if (childrenBitmap == 0) {
            throw new NodeDecodingException("Zero children bitmap");
        }
        return childrenBitmap;
    }

    /**
     * Extracts the storage value from a node in a trie structure.
     *
     * @param reader             The ScaleCodecReader used to read the encoded node data.
     * @param storageValueHashed A Boolean indicating whether the storage value is hashed. Can be null, true, or false.
     * @return byte[] The storage value as a byte array, or null if there is no storage value.
     * @throws NodeDecodingException If there are no more bytes to read from the reader.
     */
    private static byte[] decodeStorageValue(ScaleCodecReader reader, @Nullable Boolean storageValueHashed) {
        if (!reader.hasNext()) {
            throw new NodeDecodingException("Node value cannot be null for storage value");
        }

        byte[] storageValue;

        if (storageValueHashed == null) {
            storageValue = null;
        } else if (!storageValueHashed) {
            storageValue = reader.readByteArray();
        } else { // storageValueHashed is true
            storageValue = reader.readByteArray(HASHED_STORAGE_VALUE_LENGTH);
        }

        return storageValue;
    }

    /**
     * Extracts the children of a node in a trie structure.
     *
     * @param reader         The ScaleCodecReader used to read the encoded node data.
     * @param childrenBitmap An integer bitmap representing the presence of children. Each bit corresponds to a child.
     * @return {@code List<List <Byte>>} A list of children, where each child is represented as a list of bytes.
     * @throws NodeDecodingException If the length of any child's data exceeds the maximum allowed size.
     */
    private static List<List<Byte>> decodeChildren(ScaleCodecReader reader, int childrenBitmap) {
        List<List<Byte>> children = new ArrayList<>(CHILDREN_COUNT);
        for (int i = 0; i < CHILDREN_COUNT; i++) {
            if ((childrenBitmap & (1 << i)) == 0) {
                children.add(i, null);
                continue;
            }
            byte[] value = reader.readByteArray();
            if (value.length > CHILD_BYTES_SIZE_LIMIT) {
                throw new NodeDecodingException("Child too large");
            }
            children.add(i, Arrays.asList(ArrayUtils.toObject(value)));
        }
        return children;
    }
}
