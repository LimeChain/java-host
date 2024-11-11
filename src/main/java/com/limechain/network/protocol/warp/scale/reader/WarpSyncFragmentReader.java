package com.limechain.network.protocol.warp.scale.reader;

import com.limechain.network.protocol.warp.dto.WarpSyncFragment;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.scale.ScaleReader;

public class WarpSyncFragmentReader implements ScaleReader<WarpSyncFragment> {
    @Override
    public WarpSyncFragment read(ScaleCodecReader reader) {
        WarpSyncFragment fragment = new WarpSyncFragment();
        fragment.setHeader(new BlockHeaderReader().read(reader));
        fragment.setJustification(new JustificationReader().read(reader));
        return fragment;
    }
}
