package com.limechain.runtime;

import com.limechain.trie.structure.nibble.Nibbles;
import lombok.AllArgsConstructor;
import lombok.Getter;

// All available keys can be found here: https://www.shawntabrizi.com/substrate-known-keys/
// Keys are encoded using 'xxHash' algorithm, and we use directly the encoded value
@Getter
@AllArgsConstructor
public enum RuntimeStorageKey {
    GENESIS_SLOT("0x1cb6f36e027abb2091cfb5110ab5087f678711d15ebbceba5cd0cea158e6675a");

    private final String encodedKey;

    public Nibbles getNibbles() {
        return Nibbles.fromHexString(this.getEncodedKey());
    }
}
