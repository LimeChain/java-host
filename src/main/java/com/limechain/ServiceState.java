package com.limechain;

public interface ServiceState {

    void initialize();

    void initializeFromDatabase();

    void persistState();
}
