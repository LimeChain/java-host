package com.limechain.network.protocol.transaction.state;

import com.limechain.transaction.TransactionState;
import com.limechain.transaction.dto.Extrinsic;
import com.limechain.transaction.dto.TransactionValidity;
import com.limechain.transaction.dto.ValidTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionStateTest {

    private static final byte[] TAG_1 = "tag1".getBytes();
    private static final byte[] TAG_2 = "tag2".getBytes();

    private TransactionState sut;

    @BeforeEach
    void setUp() {
        sut = new TransactionState();
    }

    @Test
    void testTransactionState() {
        ValidTransaction[] validTransactions = new ValidTransaction[]{
                new ValidTransaction(new Extrinsic(new byte[]{'a'}), new TransactionValidity()),
                new ValidTransaction(new Extrinsic(new byte[]{'b'}), new TransactionValidity()),
                new ValidTransaction(new Extrinsic(new byte[]{'c'}), new TransactionValidity()),
                new ValidTransaction(new Extrinsic(new byte[]{'d'}), new TransactionValidity()),
                new ValidTransaction(new Extrinsic(new byte[]{'e'}), new TransactionValidity())
        };
        for (ValidTransaction validTransaction :
                validTransactions) {
            sut.addToPool(validTransaction);
        }

        ValidTransaction[] pendingInPool = sut.pendingInPool();

        Arrays.sort(pendingInPool, Comparator.comparing(a -> new String(a.getExtrinsic().getData())));

        assertArrayEquals(validTransactions, pendingInPool);
        assertNull(sut.peek());
    }

    @Test
    void testShouldAddToQueue_AllRequiredProvided() {
        TransactionValidity validity1 = new TransactionValidity(
                BigInteger.TEN, null, new byte[][]{TAG_1, TAG_2}, BigInteger.ZERO, true);
        ValidTransaction existingTransaction = new ValidTransaction(null, validity1);

        sut.pushTransaction(existingTransaction);

        TransactionValidity validity2 = new TransactionValidity(
                BigInteger.ONE, new byte[][]{TAG_1, TAG_2}, null, BigInteger.ZERO, true);
        ValidTransaction validTransaction = new ValidTransaction(null, validity2);

        boolean result = sut.shouldAddToQueue(validTransaction);
        assertTrue(result);
    }

    @Test
    void testShouldAddToQueue_NotAllRequiredProvided() {
        TransactionValidity validity1 = new TransactionValidity(
                BigInteger.TEN, null, new byte[][]{TAG_1}, BigInteger.ZERO, true);
        ValidTransaction existingTransaction = new ValidTransaction(null, validity1);

        sut.pushTransaction(existingTransaction);

        TransactionValidity validity2 = new TransactionValidity(
                BigInteger.ONE, new byte[][]{TAG_1, TAG_2}, null, BigInteger.ZERO, true);
        ValidTransaction validTransaction = new ValidTransaction(null, validity2);

        boolean result = sut.shouldAddToQueue(validTransaction);
        assertFalse(result);
    }

    @Test
    void testShouldAddToQueue_EmptyTransactionQueue() {
        TransactionValidity validity2 = new TransactionValidity(
                BigInteger.ONE, new byte[][]{TAG_1}, null, BigInteger.ZERO, true);
        ValidTransaction validTransaction = new ValidTransaction(null, validity2);

        boolean result = sut.shouldAddToQueue(validTransaction);
        assertFalse(result);
    }

    @Test
    void testShouldAddToQueue_TransactionHasNoRequires() {
        TransactionValidity validity1 = new TransactionValidity(
                BigInteger.TEN, null, new byte[][]{TAG_1}, BigInteger.ZERO, true);
        ValidTransaction existingTransaction = new ValidTransaction(null, validity1);

        sut.pushTransaction(existingTransaction);

        TransactionValidity validity2 = new TransactionValidity(
                BigInteger.ONE, new byte[][]{}, null, BigInteger.ZERO, true);
        ValidTransaction validTransaction = new ValidTransaction(null, validity2);

        boolean result = sut.shouldAddToQueue(validTransaction);
        assertTrue(result);
    }

    @Test
    void testShouldAddToQueue_TransactionHasNoProvides() {
        TransactionValidity validity1 = new TransactionValidity(
                BigInteger.TEN, null, new byte[][]{}, BigInteger.ZERO, true);
        ValidTransaction existingTransaction = new ValidTransaction(null, validity1);

        sut.pushTransaction(existingTransaction);

        TransactionValidity validity2 = new TransactionValidity(
                BigInteger.ONE, new byte[][]{TAG_1}, null, BigInteger.ZERO, true);
        ValidTransaction validTransaction = new ValidTransaction(null, validity2);

        boolean result = sut.shouldAddToQueue(validTransaction);
        assertFalse(result);
    }
}
