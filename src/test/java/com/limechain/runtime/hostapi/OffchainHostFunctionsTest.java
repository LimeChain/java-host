package com.limechain.runtime.hostapi;

import com.limechain.exception.hostapi.InvalidArgumentException;
import com.limechain.runtime.SharedMemory;
import com.limechain.runtime.hostapi.dto.HttpErrorType;
import com.limechain.runtime.hostapi.dto.HttpStatusCode;
import com.limechain.runtime.hostapi.dto.OffchainNetworkState;
import com.limechain.runtime.hostapi.dto.RuntimePointerSize;
import com.limechain.storage.offchain.OffchainStorages;
import com.limechain.storage.offchain.OffchainStore;
import com.limechain.utils.scale.ScaleUtils;
import io.emeraldpay.polkaj.scale.ScaleCodecWriter;
import io.emeraldpay.polkaj.scale.writer.UInt64Writer;
import org.javatuples.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OffchainHostFunctionsTest {
    private OffchainHostFunctions offchainHostFunctions;

    @Mock
    private OffchainHttpRequests requests;
    @Mock
    private SharedMemory sharedMemory;
    @Mock
    private OffchainStore persistentStorage;
    @Mock
    private OffchainStore localStorage;
    @Mock
    private RuntimePointerSize runtimePointerSize;
    @Mock
    private RuntimePointerSize resultPointer;
    @Mock
    private RuntimePointerSize deadlinePointer;
    @Mock
    private RuntimePointerSize bufferPointer;

    final int timeout = 10;
    final int requestId = 1;
    Map<String, List<String>> responseHeaders = new HashMap<>() {
        {
            put("Test", new ArrayList<>() {
                {
                    add("List");
                }
            });
        }
    };

    @BeforeEach
    void setup() {
        OffchainStorages offchainStorages = new OffchainStorages(localStorage, persistentStorage, persistentStorage);
        OffchainNetworkState networkState = null; // NOTE: Intentionally null as it's not needed for the test suite
        offchainHostFunctions = new OffchainHostFunctions(sharedMemory, offchainStorages, networkState, false, requests);
    }

    @Test
    void extOffchainTimestampShouldReturnCurrentTimeFromInstant() {
        Instant instant = mock(Instant.class);
        long time = 123L;

        try (MockedStatic<Instant> mockedStatic = mockStatic(Instant.class)) {
            mockedStatic.when(Instant::now).thenReturn(instant);
            when(instant.toEpochMilli()).thenReturn(time);

            long result = offchainHostFunctions.extOffchainTimestamp();

            assertEquals(time, result);
        }
    }

    @Test
    void extOffchainRandomSeedReturnsAPointerToA32BitNumber() {
        RuntimePointerSize runtimePointerSize = mock(RuntimePointerSize.class);
        int pointer = 123;
        when(sharedMemory.writeData(argThat(argument -> argument.length == 32))).thenReturn(runtimePointerSize);
        when(runtimePointerSize.pointer()).thenReturn(pointer);

        int result = offchainHostFunctions.extOffchainRandomSeed();

        assertEquals(pointer, result);
    }

    @Test
    void extOffchainLocalStorageSetWhenKindIs1ShouldStoreKeyValueInPersistentStorage() {
        RuntimePointerSize keyPointer = mock(RuntimePointerSize.class);
        RuntimePointerSize valuePointer = mock(RuntimePointerSize.class);
        byte[] key = "key".getBytes();
        byte[] value = new byte[] {1,2,3};
        when(sharedMemory.readData(keyPointer)).thenReturn(key);
        when(sharedMemory.readData(valuePointer)).thenReturn(value);

        offchainHostFunctions.extOffchainLocalStorageSet(1, keyPointer, valuePointer);

        verify(persistentStorage).set(key, value);
    }

    @Test
    void extOffchainLocalStorageSetWhenKindIs2ShouldStoreKeyValueInLocalStorage() {
        RuntimePointerSize keyPointer = mock(RuntimePointerSize.class);
        RuntimePointerSize valuePointer = mock(RuntimePointerSize.class);
        byte[] key = "key".getBytes();
        byte[] value = new byte[] {1,2,3};
        when(sharedMemory.readData(keyPointer)).thenReturn(key);
        when(sharedMemory.readData(valuePointer)).thenReturn(value);

        offchainHostFunctions.extOffchainLocalStorageSet(2, keyPointer, valuePointer);

        verify(localStorage).set(key, value);
    }

    @Test
    void extOffchainLocalStorageSetWhenKindIsNot1Or2ShouldThrow() {
        RuntimePointerSize keyPointer = mock(RuntimePointerSize.class);
        RuntimePointerSize valuePointer = mock(RuntimePointerSize.class);

        assertThrows(InvalidArgumentException.class, () ->
            offchainHostFunctions.extOffchainLocalStorageSet(0, keyPointer, valuePointer)
        );
    }

    @Test
    void extOffchainLocalStorageClearWhenKindIs1ShouldClearKeyInPersistentStorage() {
        RuntimePointerSize keyPointer = mock(RuntimePointerSize.class);
        String key = "key";
        when(sharedMemory.readData(keyPointer)).thenReturn(key.getBytes());

        offchainHostFunctions.extOffchainLocalStorageClear(1, keyPointer);

        verify(persistentStorage).remove(key.getBytes());
    }

    @Test
    void extOffchainLocalStorageClearWhenKindIs2ShouldClearKeyInLocalStorage() {
        RuntimePointerSize keyPointer = mock(RuntimePointerSize.class);
        String key = "key";
        when(sharedMemory.readData(keyPointer)).thenReturn(key.getBytes());

        offchainHostFunctions.extOffchainLocalStorageClear(2, keyPointer);

        verify(localStorage).remove(key.getBytes());
    }

    @Test
    void extOffchainLocalStorageClearWhenKindIsNot1Or2ShouldThrow() {
        RuntimePointerSize keyPointer = mock(RuntimePointerSize.class);

        assertThrows(InvalidArgumentException.class, () ->
                offchainHostFunctions.extOffchainLocalStorageClear(0, keyPointer)
        );
    }

    @Test
    void extOffchainLocalStorageCompareAndSetWhenKindIs1AndPersistentStorageSetIsSuccessfulReturn1() {
        RuntimePointerSize keyPointer = mock(RuntimePointerSize.class);
        RuntimePointerSize oldValuePointer = mock(RuntimePointerSize.class);
        RuntimePointerSize newValuePointer = mock(RuntimePointerSize.class);

        byte[] key = "key".getBytes();
        byte[] oldValue = new byte[] {1,2,3};
        byte[] oldValueOption = new byte[] {1,12,1,2,3}; // 1 - non-empty, 12 - compact value of 3 (size of the value)
        byte[] newValue = new byte[] {4,5,6};
        when(sharedMemory.readData(keyPointer)).thenReturn(key);
        when(sharedMemory.readData(oldValuePointer)).thenReturn(oldValueOption);
        when(sharedMemory.readData(newValuePointer)).thenReturn(newValue);
        when(persistentStorage.compareAndSet(key, oldValue, newValue)).thenReturn(true);

        int result = offchainHostFunctions.extOffchainLocalStorageCompareAndSet(1, keyPointer,
                oldValuePointer, newValuePointer);

        assertEquals(1, result);
    }

    @Test
    void extOffchainLocalStorageCompareAndSetWhenKindIs2AndLocalStorageSetIsSuccessfulReturn1() {
        RuntimePointerSize keyPointer = mock(RuntimePointerSize.class);
        RuntimePointerSize oldValuePointer = mock(RuntimePointerSize.class);
        RuntimePointerSize newValuePointer = mock(RuntimePointerSize.class);

        byte[] key = "key".getBytes();
        byte[] oldValue = new byte[] {1,2,3};
        byte[] oldValueOption = new byte[] {1,12,1,2,3}; // 1 - non-empty, 12 - compact value of 3 (size of the value)
        byte[] newValue = new byte[] {4,5,6};
        when(sharedMemory.readData(keyPointer)).thenReturn(key);
        when(sharedMemory.readData(oldValuePointer)).thenReturn(oldValueOption);
        when(sharedMemory.readData(newValuePointer)).thenReturn(newValue);
        when(localStorage.compareAndSet(key, oldValue, newValue)).thenReturn(true);

        int result = offchainHostFunctions.extOffchainLocalStorageCompareAndSet(2, keyPointer,
                oldValuePointer, newValuePointer);

        assertEquals(1, result);
    }

    @Test
    void extOffchainLocalStorageCompareAndSetWhenKindIs1AndPersistentStorageSetFailsReturn0() {
        RuntimePointerSize keyPointer = mock(RuntimePointerSize.class);
        RuntimePointerSize oldValuePointer = mock(RuntimePointerSize.class);
        RuntimePointerSize newValuePointer = mock(RuntimePointerSize.class);

        byte[] key = "key".getBytes();
        byte[] oldValue = new byte[] {1,2,3};
        byte[] oldValueOption = new byte[] {1,12,1,2,3}; // 1 - non-empty, 12 - compact value of 3 (size of the value)
        byte[] newValue = new byte[] {4,5,6};
        when(sharedMemory.readData(keyPointer)).thenReturn(key);
        when(sharedMemory.readData(oldValuePointer)).thenReturn(oldValueOption);
        when(sharedMemory.readData(newValuePointer)).thenReturn(newValue);
        when(persistentStorage.compareAndSet(key, oldValue, newValue)).thenReturn(false);

        int result = offchainHostFunctions.extOffchainLocalStorageCompareAndSet(1, keyPointer,
                oldValuePointer, newValuePointer);

        assertEquals(0, result);
    }

    @Test
    void extOffchainLocalStorageCompareAndSetWhenKindIs2AndLocalStorageSetFailsReturn0() {
        RuntimePointerSize keyPointer = mock(RuntimePointerSize.class);
        RuntimePointerSize oldValuePointer = mock(RuntimePointerSize.class);
        RuntimePointerSize newValuePointer = mock(RuntimePointerSize.class);

        byte[] key = "key".getBytes();
        byte[] oldValue = new byte[] {1,2,3};
        byte[] oldValueOption = new byte[] {1,12,1,2,3}; // 1 - non-empty, 12 - compact value of 3 (size of the value)
        byte[] newValue = new byte[] {4,5,6};
        when(sharedMemory.readData(keyPointer)).thenReturn(key);
        when(sharedMemory.readData(oldValuePointer)).thenReturn(oldValueOption);
        when(sharedMemory.readData(newValuePointer)).thenReturn(newValue);
        when(localStorage.compareAndSet(key, oldValue, newValue)).thenReturn(false);

        int result = offchainHostFunctions.extOffchainLocalStorageCompareAndSet(2, keyPointer,
                oldValuePointer, newValuePointer);

        assertEquals(0, result);
    }

    @Test
    void extOffchainLocalStorageCompareAndSetWhenKindIsNot1Or2ShouldThrow() {
        RuntimePointerSize keyPointer = mock(RuntimePointerSize.class);
        RuntimePointerSize oldValuePointer = mock(RuntimePointerSize.class);
        RuntimePointerSize newValuePointer = mock(RuntimePointerSize.class);

        assertThrows(InvalidArgumentException.class, () ->
                offchainHostFunctions.extOffchainLocalStorageCompareAndSet(0,
                        keyPointer, oldValuePointer, newValuePointer)
        );
    }

    @Test
    void extOffchainLocalStorageGetWhenKindIs1ShouldGetValueFromPersistentStorage() {
        RuntimePointerSize keyPointer = mock(RuntimePointerSize.class);
        RuntimePointerSize valuePointer = mock(RuntimePointerSize.class);
        byte[] key = "key".getBytes();
        byte[] value = new byte[] {1,2,3};
        byte[] valueAsOption = new byte[] {1,1,2,3};
        when(sharedMemory.readData(keyPointer)).thenReturn(key);
        when(persistentStorage.get(key)).thenReturn(value);
        when(sharedMemory.writeData(valueAsOption)).thenReturn(valuePointer);

        RuntimePointerSize result = offchainHostFunctions.extOffchainLocalStorageGet(1, keyPointer);

        assertEquals(valuePointer, result);
    }

    @Test
    void extOffchainLocalStorageGetWhenKindIs2ShouldGetValueFromLocalStorage() {
        RuntimePointerSize keyPointer = mock(RuntimePointerSize.class);
        RuntimePointerSize valuePointer = mock(RuntimePointerSize.class);
        byte[] key = "key".getBytes();
        byte[] value = new byte[] {1,2,3};
        byte[] valueAsOption = new byte[] {1,1,2,3};
        when(sharedMemory.readData(keyPointer)).thenReturn(key);
        when(localStorage.get(key)).thenReturn(value);
        when(sharedMemory.writeData(valueAsOption)).thenReturn(valuePointer);

        RuntimePointerSize result = offchainHostFunctions.extOffchainLocalStorageGet(2, keyPointer);

        assertEquals(valuePointer, result);
    }

    @Test
    void extOffchainLocalStorageGetWhenKindIsNot1Or2ShouldThrow() {
        RuntimePointerSize keyPointer = mock(RuntimePointerSize.class);

        assertThrows(InvalidArgumentException.class, () ->
                offchainHostFunctions.extOffchainLocalStorageGet(0, keyPointer)
        );
    }

    @Test
    void offchainIndexSetShouldSetInPersistentStorage() {
        RuntimePointerSize keyPointer = mock(RuntimePointerSize.class);
        RuntimePointerSize valuePointer = mock(RuntimePointerSize.class);
        byte[] key = "key".getBytes();
        byte[] value = new byte[] {1,2,3};
        when(sharedMemory.readData(keyPointer)).thenReturn(key);
        when(sharedMemory.readData(valuePointer)).thenReturn(value);

        offchainHostFunctions.offchainIndexSet(keyPointer, valuePointer);

        verify(persistentStorage).set(key, value);
    }

    @Test
    void offchainIndexClearShouldRemoveKeyFromPersistentStorage() {
        RuntimePointerSize keyPointer = mock(RuntimePointerSize.class);
        byte[] key = "key".getBytes();
        when(sharedMemory.readData(keyPointer)).thenReturn(key);

        offchainHostFunctions.offchainIndexClear(keyPointer);

        verify(persistentStorage).remove(key);
    }

    @Test
    void extOffchainHttpResponseHeadersVersion1Test() {
        try {
            when(requests.getResponseHeaders(requestId)).thenReturn(responseHeaders);
            byte[] scaleEncodedHeaders = scaleEncodeHeaders(responseHeaders);
            when(sharedMemory.writeData(scaleEncodedHeaders)).thenReturn(resultPointer);

            RuntimePointerSize result = offchainHostFunctions.extOffchainHttpResponseHeadersVersion1(requestId);
            assertEquals(resultPointer, result);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] scaleEncodeHeaders(Map<String, List<String>> headers) throws IOException {
        List<Pair<String, String>> pairs = new ArrayList<>(headers.size());
        headers.forEach((key, values) ->
                values.forEach(value ->
                        pairs.add(new Pair<>(key, value))
                ));
        return ScaleUtils.Encode.encodeListOfPairs(pairs, String::getBytes, String::getBytes);
    }

    @Test
    void extOffchainHttpResponseWaitVersion1Test() throws IOException, InterruptedException {
        int scaleOptionalTrue = 1;
        byte[] scaleEncodedRequestIds = new byte[]{4, requestId, 0, 0, 0};

        Instant instant = Instant.now();
        MockedStatic<Instant> mockedStatic = mockStatic(Instant.class);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        ScaleCodecWriter writer = new ScaleCodecWriter(buf);
        UInt64Writer int64Writer = new UInt64Writer();
        writer.writeByte(scaleOptionalTrue);
        int64Writer.write(writer, new BigInteger(String.valueOf(instant.toEpochMilli()))
                .add(BigInteger.valueOf(timeout)));
        mockedStatic.when(Instant::now).thenReturn(instant);

        when(sharedMemory.readData(deadlinePointer)).thenReturn(buf.toByteArray());
        HttpStatusCode[] expectedResponses = new HttpStatusCode[]{HttpStatusCode.success(200)};
        when(requests.getRequestsResponses(new int[]{requestId}, timeout)).thenReturn(expectedResponses);

        when(sharedMemory.readData(runtimePointerSize)).thenReturn(scaleEncodedRequestIds);
        when(sharedMemory.writeData(offchainHostFunctions
                .scaleEncodeArrayOfRequestStatuses(expectedResponses))).thenReturn(resultPointer);

        RuntimePointerSize result = offchainHostFunctions
                .extOffchainHttpResponseWaitVersion1(runtimePointerSize, deadlinePointer);
        assertEquals(resultPointer, result);
        mockedStatic.close();
    }

    @Test
    void extOffchainHttpResponseBodyVersion1ReturnsDeadlineReachedTest() throws IOException {
        int deadlineReachedTimeout = -1;
        Instant instant = Instant.now();
        MockedStatic<Instant> mockedStatic = mockStatic(Instant.class);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        ScaleCodecWriter writer = new ScaleCodecWriter(buf);
        UInt64Writer int64Writer = new UInt64Writer();
        int scaleOptionalTrue = 1;
        writer.writeByte(scaleOptionalTrue);
        int64Writer.write(writer, new BigInteger(String.valueOf(instant.toEpochMilli()))
                .add(BigInteger.valueOf(deadlineReachedTimeout)));

        mockedStatic.when(Instant::now).thenReturn(instant);
        when(sharedMemory.readData(deadlinePointer)).thenReturn(buf.toByteArray());
        when(requests.executeRequest(eq(requestId), eq(deadlineReachedTimeout), anyLong()))
                .thenReturn(HttpStatusCode.error(HttpErrorType.DEADLINE_REACHED));
        when(sharedMemory.writeData(HttpErrorType.DEADLINE_REACHED.scaleEncodedResult()))
                .thenReturn(resultPointer);

        RuntimePointerSize result = offchainHostFunctions.extOffchainHttpResponseReadBodyVersion1(requestId,
                bufferPointer,
                deadlinePointer);
        assertEquals(resultPointer, result);
        mockedStatic.close();
    }

    @Test
    void extOffchainHttpResponseBodyVersion1ReturnsIOErrorTest() throws IOException {
        Instant instant = Instant.now();
        MockedStatic<Instant> mockedStatic = mockStatic(Instant.class);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        ScaleCodecWriter writer = new ScaleCodecWriter(buf);
        UInt64Writer int64Writer = new UInt64Writer();
        int scaleOptionalTrue = 1;
        writer.writeByte(scaleOptionalTrue);
        int64Writer.write(writer, new BigInteger(String.valueOf(instant.toEpochMilli()))
                .add(BigInteger.valueOf(timeout)));

        mockedStatic.when(Instant::now).thenReturn(instant);

        when(sharedMemory.readData(deadlinePointer)).thenReturn(buf.toByteArray());
        when(requests.executeRequest(eq(requestId), eq(timeout), anyLong()))
                .thenReturn(HttpStatusCode.error(HttpErrorType.IO_ERROR));

        when(sharedMemory.writeData(HttpErrorType.IO_ERROR.scaleEncodedResult()))
                .thenReturn(resultPointer);

        RuntimePointerSize result = offchainHostFunctions.extOffchainHttpResponseReadBodyVersion1(requestId,
                bufferPointer,
                deadlinePointer);
        assertEquals(resultPointer, result);
        mockedStatic.close();
    }

    @Test
    void extOffchainHttpResponseBodyVersion1ReturnsInvalidIdTest() throws IOException {
        Instant instant = Instant.now();

        MockedStatic<Instant> mockedStatic = mockStatic(Instant.class);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        ScaleCodecWriter writer = new ScaleCodecWriter(buf);
        UInt64Writer int64Writer = new UInt64Writer();
        int scaleOptionalTrue = 1;
        writer.writeByte(scaleOptionalTrue);
        int64Writer.write(writer, new BigInteger(String.valueOf(instant.toEpochMilli()))
                .add(BigInteger.valueOf(timeout)));

        mockedStatic.when(Instant::now).thenReturn(instant);

        when(sharedMemory.readData(deadlinePointer)).thenReturn(buf.toByteArray());
        when(requests.executeRequest(eq(requestId), eq(timeout), anyLong()))
                .thenReturn(HttpStatusCode.error(HttpErrorType.INVALID_ID));

        when(sharedMemory.writeData(HttpErrorType.INVALID_ID.scaleEncodedResult()))
                .thenReturn(resultPointer);

        RuntimePointerSize result = offchainHostFunctions.extOffchainHttpResponseReadBodyVersion1(requestId,
                bufferPointer,
                deadlinePointer);
        assertEquals(resultPointer, result);
        mockedStatic.close();
    }

}