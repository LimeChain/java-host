package com.limechain.chain.spec;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.google.protobuf.ByteString;
import com.limechain.utils.StringUtils;
import lombok.Getter;
import org.apache.tomcat.util.buf.HexUtils;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Genesis implements Serializable {
    private Map<ByteString, ByteString> top;

    // NOTE: Figure out appropriate types when necessary, intentionally unused for now
    private Map<String, String> childrenDefault;

    @JsonGetter("top")
    private Map<String, String> jsonGetTop() {
        Function<ByteString, String> serializer =
            bs -> "0x" + HexUtils.toHexString(bs.toByteArray());

        return this.top.entrySet().stream().collect(Collectors.toMap(
            e -> serializer.apply(e.getKey()),
            e -> serializer.apply(e.getValue())
        ));
    }

    @JsonSetter("top")
    private void jsonSetTop(Map<String, String> deserializedTop) {
        Function<String, ByteString> parser =
            hex -> ByteString.fromHex(StringUtils.remove0xPrefix(hex));

        this.top = deserializedTop.entrySet().stream().collect(Collectors.toMap(
            e -> parser.apply(e.getKey()),
            e -> parser.apply(e.getValue())
        ));
    }

    public Map<ByteString, ByteString> getTop() {
        return Collections.unmodifiableMap(this.top);
    }
}
