package com.limechain.network.protocol.warp.scale.reader;

import com.limechain.network.protocol.warp.dto.WarpSyncFragment;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.scale.ScaleReader;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class WarpSyncFragmentReader implements ScaleReader<WarpSyncFragment> {

    private static final WarpSyncFragmentReader INSTANCE = new WarpSyncFragmentReader();

    public static WarpSyncFragmentReader getInstance() {
        return INSTANCE;
    }

    @Override
    public WarpSyncFragment read(ScaleCodecReader reader) {
        WarpSyncFragment fragment = new WarpSyncFragment();
        fragment.setHeader(BlockHeaderReader.getInstance().read(reader));
        fragment.setJustification(JustificationReader.getInstance().read(reader));
        return fragment;
    }
}
