package com.limechain.grandpa;

import com.limechain.grandpa.state.GrandpaState;
import lombok.extern.java.Log;
import org.springframework.stereotype.Component;

@Log
@Component
public class GrandpaService {
    private final GrandpaState grandpaState;

    public GrandpaService(GrandpaState grandpaState) {
        this.grandpaState = grandpaState;
    }

    private void getGrandpaGhost(){}
}
