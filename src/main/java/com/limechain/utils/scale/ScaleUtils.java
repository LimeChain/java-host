package com.limechain.utils.scale;

import com.limechain.exception.scale.ScaleDecodingException;
import com.limechain.exception.scale.ScaleEncodingException;
import com.limechain.utils.scale.writers.IterableWriter;
import com.limechain.utils.scale.writers.PairWriter;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.scale.ScaleCodecWriter;
import io.emeraldpay.polkaj.scale.ScaleReader;
import io.emeraldpay.polkaj.scale.ScaleWriter;
import io.emeraldpay.polkaj.scale.reader.ListReader;
import io.emeraldpay.polkaj.scale.writer.ListWriter;
import lombok.experimental.UtilityClass;
import org.javatuples.Pair;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

// TODO:
//  This is currently a helper utility class
//  planned to grow into a unified scale encode/decode util class with whatever methods are useful
//  WIP
// Currently trying out different approaches to spare some of the boilerplate around SCALE en/decoding
@UtilityClass
public class ScaleUtils {

    /**
     * A utility method that returns true if the scale decoded result is successful. See
     * <a href="https://docs.substrate.io/reference/scale-codec/">Results</a> section.
     *
     * @param reader a reader with preloaded byte data.
     * @return true if result byte is 0, false otherwise.
     */
    public boolean isScaleResultSuccessful(ScaleCodecReader reader) {
        return reader.readUByte() == 0;
    }

    @UtilityClass
    public class Decode {

        /**
         * Decodes a byte array into an object of type T using the provided ScaleReader.
         *
         * @param encodedData The byte array containing the encoded data.
         * @param reader      The ScaleReader implementation for decoding.
         * @param <T>         The type of object to decode.
         * @return The decoded object of type T.
         * @throws ScaleDecodingException If an error occurs during decoding.
         */
        public <T> T decode(byte[] encodedData, ScaleReader<T> reader) {
            try {
                return new ScaleCodecReader(encodedData).read(reader);
            } catch (RuntimeException e) {
                throw new ScaleDecodingException("Error while SCALE decoding.", e);
            }
        }

        /**
         * Decodes a byte array representing a list of items into a List using the provided ScaleReader for the list items.
         *
         * @param encodedData    The byte array containing the encoded list.
         * @param listItemReader The ScaleReader implementation for decoding individual list items.
         * @param <T>            The type of objects in the list.
         * @return The decoded List of items.
         * @throws ScaleDecodingException If an error occurs during decoding.
         */
        public <T> List<T> decodeList(byte[] encodedData, ScaleReader<T> listItemReader) {
            return decode(encodedData, new ListReader<>(listItemReader));
        }
    }

    @UtilityClass
    public class Encode {

        /**
         * Encodes a list of pairs into SCALE format using the provided serializers for the key and value types.
         *
         * @param pairs         The list of pairs to encode.
         * @param fstSerializer The serializer function for the first element of each pair.
         * @param sndSerializer The serializer function for the second element of each pair.
         * @param <K>           The type of the first element in the pairs.
         * @param <V>           The type of the second element in the pairs.
         * @return The encoded byte array representing the list of pairs.
         */
        public <K, V> byte[] encodeListOfPairs(
                List<Pair<K, V>> pairs,
                Function<K, byte[]> fstSerializer,
                Function<V, byte[]> sndSerializer
        ) {
            return encodeListOfPairs(
                    pairs.stream()
                            .map(p ->
                                    new Pair<>(
                                            fstSerializer.apply(p.getValue0()),
                                            sndSerializer.apply(p.getValue1())))
                            .toList());
        }

        /**
         * Encodes a list of pairs into SCALE format using default pair serialization.
         *
         * @param pairs The list of pairs to encode.
         * @return The encoded byte array representing the list of pairs.
         */
        public byte[] encodeListOfPairs(List<Pair<byte[], byte[]>> pairs) {
            return encode(
                    new ListWriter<>(
                            new PairWriter<>(
                                    ScaleCodecWriter::writeAsList,
                                    ScaleCodecWriter::writeAsList)),
                    pairs);
        }

        /**
         * Encodes an Iterable of bytes into SCALE format.
         *
         * @param bytes The Iterable of bytes to encode.
         * @return The encoded byte array.
         */
        public byte[] encodeAsListOfBytes(Iterable<Byte> bytes) {
            return encodeAsList(ScaleCodecWriter::directWrite, bytes);
        }

        /**
         * Encodes an Iterable of values into SCALE format using the provided ScaleWriter.
         *
         * @param collectionItemWriter The ScaleWriter for encoding each item.
         * @param values               The Iterable of values to encode.
         * @param <T>                  The type of values to encode.
         * @return The encoded byte array.
         */
        public <T> byte[] encodeAsList(ScaleWriter<T> collectionItemWriter, Iterable<T> values) {
            return encode(new IterableWriter<>(collectionItemWriter), values);
        }

        /**
         * Encodes a two-dimensional byte array into SCALE format.
         *
         * @param values The two-dimensional byte array to encode.
         * @return The encoded byte array.
         */
        public byte[] encode(byte[][] values) {
            return encode(new ListWriter<>(ScaleCodecWriter::writeAsList), Arrays.asList(values));
        }

        /**
         * Encodes an object into SCALE format using the provided ScaleWriter.
         *
         * @param writer The ScaleWriter for encoding the object.
         * @param value  The object to encode.
         * @param <T>    The type of the object to encode.
         * @return The encoded byte array.
         * @throws ScaleEncodingException If an unexpected error occurs during encoding.
         */
        public <T> byte[] encode(ScaleWriter<T> writer, T value) {
            try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                 ScaleCodecWriter scaleCodecWriter = new ScaleCodecWriter(buffer)) {
                writer.write(scaleCodecWriter, value);
                return buffer.toByteArray();
            } catch (IOException e) {
                throw new ScaleEncodingException("Unexpected exception while encoding.");
            }
        }

        /**
         * Scale encodes a nullable value as an optional.
         * If the value is null, it is encoded as an empty optional.
         * If the value is not null, it is encoded as an optional with a present value.
         *
         * @param writer The ScaleWriter for encoding the value, <strong>if</strong> not null.
         * @param value  The nullable object to encode.
         * @param <T>
         * @return The encoded optional value.
         * @throws ScaleEncodingException If an unexpected error occurs during encoding.
         */
        public <T> byte[] encodeOptional(ScaleWriter<T> writer, @Nullable T value) {
            return ScaleUtils.Encode.encode(
                    (scaleCodecWriter, val) -> scaleCodecWriter.writeOptional(writer, val),
                    value
            );
        }
    }
}
