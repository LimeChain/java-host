package com.limechain.runtime.hostapi.scale;

import io.emeraldpay.polkaj.scale.ScaleCodecWriter;
import io.emeraldpay.polkaj.scale.ScaleWriter;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.io.IOException;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ResultWriter implements ScaleWriter<byte[]> {

    private static final ResultWriter INSTANCE = new ResultWriter();

    public static ResultWriter getInstance() {
        return INSTANCE;
    }

    @Override
    public void write(ScaleCodecWriter scaleCodecWriter, byte[] bytes) throws IOException {
        scaleCodecWriter.writeByteArray(bytes);
    }
    
    public void writeResult(ScaleCodecWriter scaleCodecWriter, boolean success) throws IOException {
        if (success) {
            scaleCodecWriter.writeByte((byte) 0);
        } else {
            scaleCodecWriter.writeByte((byte) 1);
        }
    }
}
