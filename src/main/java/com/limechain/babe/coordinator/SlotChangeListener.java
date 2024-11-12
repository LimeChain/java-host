package com.limechain.babe.coordinator;

import java.util.EventListener;

public interface SlotChangeListener extends EventListener {
    void slotChanged(SlotChangeEvent event);
}