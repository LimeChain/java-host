package com.limechain.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.AbstractMap;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DBInitializerTest {
    private DBInitializer initializer;

    @BeforeEach
    public void setup() {
        this.initializer = new DBInitializer();
    }

    @AfterEach
    public void close() {
        this.initializer.closeInstances();
    }

    // Setting private fields. Not a good idea in general
    private void setPrivateField(String fieldName, Object value)
            throws NoSuchFieldException, IllegalAccessException {
        Field privateField = DBInitializer.class.getDeclaredField(fieldName);
        privateField.setAccessible(true);

        privateField.set(initializer, value);
    }

    // Accessing private fields. Not a good idea in general
    private Object getPrivateField(String fieldName)
            throws NoSuchFieldException, IllegalAccessException {
        Field privateField = DBInitializer.class.getDeclaredField(fieldName);
        privateField.setAccessible(true);

        return privateField.get(initializer);
    }

    @Test
    public void initialize_addsRepository() throws NoSuchFieldException, IllegalAccessException {
        DBInitializer initializer = mock(DBInitializer.class);
        Map<String, DBRepository> instances = mock(Map.class);
        setPrivateField("instances", instances);
        initializer.initialize("path1");

        verify(instances, times(1)).put(eq("path1"), any());
        verify(instances, never()).get("path1");

        when(instances.containsKey("path1")).thenReturn(true);

        initializer.initialize("path1");

        verify(instances, times(1)).get("path1");
        verify(instances, times(1)).put(eq("path1"), any());
    }

    @Test
    public void closeInstances_closesConnection() throws NoSuchFieldException, IllegalAccessException {
        Map<String, DBRepository> instances = mock(Map.class);
        Map.Entry<String, DBRepository> entrySet1 = new AbstractMap.SimpleEntry("path1", mock(DBRepository.class));
        Map.Entry<String, DBRepository> entrySet2 = new AbstractMap.SimpleEntry("path2", mock(DBRepository.class));

        setPrivateField("instances", instances);
        Set<Map.Entry<String, DBRepository>> set = Set.of(entrySet1, entrySet2);
        when(instances.entrySet()).thenReturn(set);

        initializer.closeInstances();
        verify(entrySet1.getValue(), times(1)).closeConnection();
        verify(entrySet2.getValue(), times(1)).closeConnection();
    }

}