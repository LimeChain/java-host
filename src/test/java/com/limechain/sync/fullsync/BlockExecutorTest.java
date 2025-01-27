package com.limechain.sync.fullsync;

import com.google.protobuf.ByteString;
import com.limechain.chain.spec.ChainSpec;
import com.limechain.network.protocol.warp.dto.Block;
import com.limechain.network.protocol.warp.dto.BlockBody;
import com.limechain.network.protocol.warp.dto.BlockHeader;
import com.limechain.network.protocol.warp.dto.HeaderDigest;
import com.limechain.network.protocol.warp.scale.reader.HeaderDigestReader;
import com.limechain.runtime.Runtime;
import com.limechain.runtime.RuntimeFactory;
import com.limechain.runtime.version.StateVersion;
import com.limechain.storage.KVRepository;
import com.limechain.storage.trie.TrieStorage;
import com.limechain.transaction.dto.Extrinsic;
import com.limechain.trie.DiskTrieAccessor;
import com.limechain.trie.TrieStructureFactory;
import com.limechain.utils.StringUtils;
import com.limechain.utils.scale.ScaleUtils;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.types.Hash256;
import lombok.extern.java.Log;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

//@formatter:off
@Log
class BlockExecutorTest {
    @Test
    void kusamaFirstBlock() throws IOException {
        // Build the genesis state trie
        ChainSpec chainSpec = ChainSpec.newFromJSON("genesis/ksmcc3.json");
        var genesisStorage = chainSpec.getGenesis().getTop();
        var runtimeByteCode = genesisStorage.get(ByteString.copyFrom(":code".getBytes())).toByteArray();
        var genesisTrie = TrieStructureFactory.buildFromKVPs(genesisStorage);

        // Define dependencies for the execution context
        KVRepository<String, Object> db = new InMemoryDB(); // initialize a DB

        TrieStorage trieStorage = new TrieStorage(db); // inject it into a trie storage
        trieStorage.insertTrieStorage(genesisTrie); // populate with the genesis trie

        final byte[] genesisStateRoot = genesisTrie.getRootNode().get().getUserData().getMerkleValue();
        DiskTrieAccessor trieAccessor = new DiskTrieAccessor(trieStorage, genesisStateRoot); // instantiate a block trie accessor with the trieStorage specified for the genesis block
        trieAccessor.setCurrentStateVersion(StateVersion.V0);

        // Package all dependencies into the expected configuration for the runtime builder
        var cfg = new RuntimeFactory.Config(trieAccessor, null, null, null, false);

        Runtime genesisRuntime = RuntimeFactory.buildRuntime(runtimeByteCode, cfg);

        // Get the first block of kusama
        Block block = getKusamaFirstBlock();

        // Execute it successfully
        assertDoesNotThrow(() -> genesisRuntime.executeBlock(block),
            "Executing the first block of Kusama must be successful.");
    }

    private Block getKusamaFirstBlock() {
        byte[] scaleEncodedBody = new byte[] {8, 40, 4, 2, 0, 11, -112, 17, 14, -77, 110, 1, 16, 4, 20, 0, 0};
        byte[] scaleEncodedDigest = StringUtils.hexToBytes("0x0c0642414245340201000000ef55a50f00000000044241424549040118ca239392960473fe1bc65f94ee27d890a49c1b200c006ff5dcc525330ecc16770100000000000000b46f01874ce7abbb5220e8fd89bede0adad14c73039d91e28e881823433e723f0100000000000000d684d9176d6eb69887540c9a89fa6097adea82fc4b0ff26d1062b488f352e179010000000000000068195a71bdde49117a616424bdc60a1733e96acb1da5aeab5d268cf2a572e94101000000000000001a0575ef4ae24bdfd31f4cb5bd61239ae67c12d4e64ae51ac756044aa6ad8200010000000000000018168f2aad0081a25728961ee00627cfe35e39833c805016632bf7c14da5800901000000000000000000000000000000000000000000000000000000000000000000000000000000054241424501014625284883e564bc1e4063f5ea2b49846cdddaa3761d04f543b698c1c3ee935c40d25b869247c36c6b8a8cbbd7bb2768f560ab7c276df3c62df357a7e3b1ec8d");

        BlockHeader header = new BlockHeader();
        header.setParentHash(Hash256.from("0xb0a8d493285c2df73290dfb7e61f870f17b41801197a149ca93654499ea3dafe"));
        header.setBlockNumber(BigInteger.valueOf(1));
        header.setStateRoot(Hash256.from("0xfabb0c6e92d29e8bb2167f3c6fb0ddeb956a4278a3cf853661af74a076fc9cb7"));
        header.setExtrinsicsRoot(Hash256.from("0xa35fb7f7616f5c979d48222b3d2fa7cb2331ef73954726714d91ca945cc34fd8"));
        header.setDigest(ScaleUtils.Decode.decodeList(scaleEncodedDigest, new HeaderDigestReader()).toArray(HeaderDigest[]::new));

        var exts = ScaleUtils.Decode.decodeList(scaleEncodedBody, ScaleCodecReader::readByteArray);
        assertEquals(2, exts.size());
        BlockBody body = new BlockBody(exts.stream().map(Extrinsic::new).toList());

        return new Block(header, body);
    }
}
