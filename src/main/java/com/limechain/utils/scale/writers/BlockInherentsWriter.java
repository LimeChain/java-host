package com.limechain.utils.scale.writers;

import com.limechain.babe.dto.InherentData;
import io.emeraldpay.polkaj.scale.ScaleCodecWriter;
import io.emeraldpay.polkaj.scale.ScaleWriter;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.javatuples.Pair;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class BlockInherentsWriter implements ScaleWriter<InherentData> {

    private static final BlockInherentsWriter INSTANCE = new BlockInherentsWriter();

    public static BlockInherentsWriter getInstance() {
        return INSTANCE;
    }

    public void write(ScaleCodecWriter writer, InherentData request) throws IOException {
        LinkedHashMap<byte[], byte[]> data = request.getData();
        writer.writeCompact(data.size());

        List<Pair<byte[], byte[]>> pairs = data.entrySet().stream()
                .map(e -> new Pair<>(e.getKey(), e.getValue()))
                .toList();

        for (Pair<byte[], byte[]> pair : pairs) {
            writer.writeByteArray(pair.getValue0());
            writer.writeAsList(pair.getValue1());
        }
    }
}
