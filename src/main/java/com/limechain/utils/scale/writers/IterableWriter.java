package com.limechain.utils.scale.writers;

import io.emeraldpay.polkaj.scale.ScaleCodecWriter;
import io.emeraldpay.polkaj.scale.ScaleWriter;
import org.apache.commons.collections4.IterableUtils;

import java.io.IOException;

public class IterableWriter<T> implements ScaleWriter<Iterable<T>> {
    private final ScaleWriter<T> scaleWriter;

    public IterableWriter(ScaleWriter<T> scaleWriter) {
        this.scaleWriter = scaleWriter;
    }

    @Override
    public void write(ScaleCodecWriter scaleCodecWriter, Iterable<T> values) throws IOException {
        scaleCodecWriter.writeCompact(IterableUtils.size(values));

        for (T item : values) {
            this.scaleWriter.write(scaleCodecWriter, item);
        }
    }
}
