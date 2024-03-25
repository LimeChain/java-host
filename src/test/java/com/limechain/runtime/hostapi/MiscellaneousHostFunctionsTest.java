package com.limechain.runtime.hostapi;

import com.limechain.rpc.server.AppBean;
import com.limechain.runtime.Runtime;
import com.limechain.runtime.hostapi.dto.RuntimePointerSize;
import com.limechain.storage.crypto.KeyStore;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MiscellaneousHostFunctionsTest {
    private final String value = "Test passed";
    private final String target = "MiscellaneousHostFunctionsTest:84";
    @InjectMocks
    private MiscellaneousHostFunctions miscellaneousHostFunctions;
    @Mock
    private Runtime runtime;
    @Mock
    private Number number;
    @Mock
    private RuntimePointerSize valuePointer;
    @Mock
    private RuntimePointerSize targetPointer;

    @Test
    void printNumV1() {
        miscellaneousHostFunctions.printNumV1(number);
        verifyNoMoreInteractions(runtime);
    }

    @Test
    void printUtf8V1() {
        when(runtime.getDataFromMemory(valuePointer)).thenReturn(value.getBytes());

        miscellaneousHostFunctions.printUtf8V1(valuePointer);

        Mockito.verify(runtime).getDataFromMemory(valuePointer);
        verifyNoMoreInteractions(runtime);
    }

    @Test
    void printHexV1() {
        when(runtime.getDataFromMemory(valuePointer)).thenReturn(value.getBytes());

        miscellaneousHostFunctions.printHexV1(valuePointer);

        Mockito.verify(runtime).getDataFromMemory(valuePointer);
        verifyNoMoreInteractions(runtime);
    }

    @Test
    @Disabled("Does not work in github actions")
    void runtimeVersionV1() throws IOException {
        byte[] wasmRuntime = Files.readAllBytes(Paths.get("src","test","resources","runtime.wasm"));
        byte[] runtimeData = Files.readAllBytes(Paths.get("src","test","resources","runtime.data"));
        when(runtime.getDataFromMemory(valuePointer)).thenReturn(wasmRuntime);
        when(runtime.writeDataToMemory(runtimeData)).thenReturn(targetPointer);

        try(MockedStatic<AppBean> appBeanMockedStatic = mockStatic(AppBean.class)){
            appBeanMockedStatic.when(() -> AppBean.getBean(KeyStore.class)).thenReturn(mock(KeyStore.class));

            RuntimePointerSize result = miscellaneousHostFunctions.runtimeVersionV1(valuePointer);

            assertEquals(targetPointer, result);
            verify(runtime).getDataFromMemory(valuePointer);
            verify(runtime).writeDataToMemory(runtimeData);
            verifyNoMoreInteractions(runtime);
        }
    }

    @Test
    void logV1() {
        when(runtime.getDataFromMemory(valuePointer)).thenReturn(value.getBytes());
        when(runtime.getDataFromMemory(targetPointer)).thenReturn(target.getBytes());

        miscellaneousHostFunctions.logV1(1, targetPointer, valuePointer);

        verify(runtime).getDataFromMemory(valuePointer);
        verify(runtime).getDataFromMemory(targetPointer);
        verifyNoMoreInteractions(runtime);
    }

    @Test
    void maxLevelV1() {
        assertEquals(4, miscellaneousHostFunctions.maxLevelV1());
        verifyNoMoreInteractions(runtime);
    }

}