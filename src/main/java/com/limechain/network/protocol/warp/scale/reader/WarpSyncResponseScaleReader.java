package com.limechain.network.protocol.warp.scale.reader;

import com.limechain.network.protocol.warp.dto.WarpSyncFragment;
import com.limechain.network.protocol.warp.dto.WarpSyncResponse;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.scale.ScaleReader;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class WarpSyncResponseScaleReader implements ScaleReader<WarpSyncResponse> {

    private static final WarpSyncResponseScaleReader INSTANCE = new WarpSyncResponseScaleReader();

    public static WarpSyncResponseScaleReader getInstance() {
        return INSTANCE;
    }

    @Override
    public WarpSyncResponse read(ScaleCodecReader reader) {
        WarpSyncResponse response = new WarpSyncResponse();
        List<WarpSyncFragment> fragments = new ArrayList<>();
        WarpSyncFragmentReader fragmentReader = WarpSyncFragmentReader.getInstance();
        int fragmentCount = reader.readCompactInt();
        for (int i = 0; i < fragmentCount; i++) {
            fragments.add(fragmentReader.read(reader));
        }
        response.setFragments(fragments.toArray(WarpSyncFragment[]::new));
        response.setFinished(reader.readBoolean());
        return response;
    }
}
