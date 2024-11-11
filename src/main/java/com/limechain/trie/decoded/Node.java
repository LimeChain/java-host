package com.limechain.trie.decoded;

import com.limechain.exception.trie.TrieEncoderException;
import com.limechain.trie.decoded.encoder.TrieEncoder;
import com.limechain.utils.HashUtils;
import lombok.Getter;
import lombok.Setter;
import org.javatuples.Pair;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

@Getter
@Setter
public class Node {
    public static final int CHILDREN_CAPACITY = 16;
    private byte[] partialKey = new byte[]{};
    private byte[] storageValue;
    private boolean isValueHashed;
    private int generation;
    private Node[] children;
    private boolean dirty;
    private byte[] merkleValue;
    private int descendants;

    /**
     * Returns the node's kind(Leaf or Branch)
     *
     * @return NodeKind
     */
    public NodeKind getKind() {
        if (this.getChildren() != null) {
            return NodeKind.BRANCH;
        }
        return NodeKind.LEAF;
    }

    /**
     * Sets a node as a child of the current node in a specific position
     *
     * @param child    Node to be set as a child
     * @param position Index of the child(0-15)
     */
    public void setChildrenAt(Node child, int position) {
        if (this.children == null) {
            this.children = new Node[CHILDREN_CAPACITY];
        }
        this.children[position] = child;
    }

    /**
     * Checks whether the node has any children
     *
     * @return True if the node has children, false otherwise
     */
    public boolean hasChild() {
        if (children == null) return false;
        for (Node child : children) {
            if (child != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns a bitmap of the node's children
     *
     * @return Bitmap of the children
     */
    public int getChildrenBitmap() {
        int bitmap = 0;
        for (int i = 0; i < this.children.length; i++) {
            if (this.children[i] != null) {
                bitmap |= 1 << i;
            }
        }
        return bitmap;
    }

    /**
     * Returns the count of node's children
     *
     * @return Count of the children
     */
    public int getChildrenCount() {
        int count = 0;
        for (Node child : this.children) {
            if (child != null) {
                count++;
            }
        }
        return count;
    }

    /**
     * @return Length of the storage value
     */
    public int getStorageValueLength() {
        if (this.storageValue == null) {
            return 0;
        }
        return this.storageValue.length;
    }

    /**
     * Returns the child of the node at a specific position
     *
     * @param pos Index of the child
     * @return child node
     */
    public Node getChild(int pos) {
        if (this.children == null) return null;

        return this.children[pos];
    }

    /**
     * Calculates the merkle value of the node
     * If the node is not dirty and the merkle value is already calculated, it will return the cached value
     * <b>Note: Has a side effect of setting the merkle value of the node</b>
     *
     * @return Merkle value
     */
    public byte[] calculateMerkleValue() {
        if (!this.isDirty() && this.getMerkleValue() != null) {
            return this.getMerkleValue();
        }

        return this.encodeAndHash().getValue1();
    }

    /**
     * Encodes the node and calculates the merkle value
     *
     * @return Pair of encoded node and merkle value
     */
    private Pair<byte[], byte[]> encodeAndHash() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            TrieEncoder.encode(this, out);
        } catch (Exception e) {
            throw new TrieEncoderException("Could not encode node: " + e.getMessage());
        }

        byte[] encoding = out.toByteArray();
        byte[] maybeHashedMerkleValue = writeMerkleValue(encoding);
        return new Pair<>(encoding, maybeHashedMerkleValue);
    }

    /**
     * Hashes the merkle value if longer than 32 bytes. Then returns it.
     *
     * @param encoding of the trie
     * @return the merkle value as a byte array
     */
    private byte[] writeMerkleValue(byte[] encoding) {
        ByteBuffer merkleValueBuf = ByteBuffer.allocate(Math.min(encoding.length, 32));
        if (encoding.length < 32) {
            merkleValueBuf.put(encoding);
        } else {
            merkleValueBuf.put(HashUtils.hashWithBlake2b(encoding));
        }
        return merkleValueBuf.array();
    }

    @Override
    public String toString() {
        return "Node{" +
                "partialKey=" + Arrays.toString(partialKey) +
                ", merkleValue=" + Arrays.toString(merkleValue) +
                ", storageValue=" + Arrays.toString(storageValue) +
                ", isValueHashed=" + isValueHashed +
                ", generation=" + generation +
                ", children=" + Arrays.toString(children) +
                ", dirty=" + dirty +
                ", descendants=" + descendants +
                '}';
    }
}
