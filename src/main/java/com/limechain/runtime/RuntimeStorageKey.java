package com.limechain.runtime;

import com.limechain.trie.structure.nibble.Nibbles;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum RuntimeStorageKey {
    GENESIS_SLOT(Nibbles.fromHexString("0x1cb6f36e027abb2091cfb5110ab5087f678711d15ebbceba5cd0cea158e6675a"));

    private final Nibbles key;
}
