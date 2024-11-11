package com.limechain.utils.scale.readers;

import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.scale.ScaleReader;
import lombok.AllArgsConstructor;
import org.javatuples.Pair;

@AllArgsConstructor
public class PairReader<K, V> implements ScaleReader<Pair<K, V>> {
    protected ScaleReader<K> firstReader;
    protected ScaleReader<V> secondReader;

    @Override
    public Pair<K, V> read(ScaleCodecReader scaleCodecReader) {
        K first = scaleCodecReader.read(firstReader);
        V second = scaleCodecReader.read(secondReader);
        return new Pair<>(first, second);
    }
}
