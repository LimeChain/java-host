package com.limechain.transaction;

import com.limechain.transaction.dto.Extrinsic;
import com.limechain.transaction.dto.ValidTransaction;
import com.limechain.utils.ByteArrayUtils;
import lombok.extern.java.Log;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@Log
@Component
public class TransactionState {

    private final TransactionPool transactionPool;
    private final Queue<ValidTransaction> transactionQueue;
    private final ExecutorService executor;

    public TransactionState() {
        transactionPool = new TransactionPool();
        executor = Executors.newSingleThreadExecutor();
        transactionQueue = new PriorityQueue<>();
    }

    public void pushTransaction(ValidTransaction validTransaction) {
        transactionQueue.add(validTransaction);
    }

    public ValidTransaction pollTransaction() {
        return transactionQueue.poll();
    }

    public ValidTransaction pollTransactionWithTimer(long timeout) {
        ValidTransaction validTransaction = pollTransaction();
        if (validTransaction != null) return validTransaction;

        Future<ValidTransaction> futureTransaction = pollFutureTransaction();

        try {
            return futureTransaction.get(timeout, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            log.severe("Error while waiting for transaction: " + e.getMessage());
        } catch (TimeoutException e) {
            futureTransaction.cancel(true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return null;
    }

    @NotNull
    private Future<ValidTransaction> pollFutureTransaction() {
        return executor.submit(() -> {
            ValidTransaction transaction = null;
            while (transaction == null) {
                transaction = pollTransaction();
                Thread.sleep(50);
            }
            return transaction;
        });
    }

    public ValidTransaction peek() {
        return transactionQueue.peek();
    }

    public ValidTransaction[] pending() {
        return transactionQueue.toArray(new ValidTransaction[0]);
    }

    public ValidTransaction[] pendingInPool() {
        return transactionPool.transactions();
    }

    public boolean existsInQueue(Extrinsic extrinsic) {
        return transactionQueue.contains(new ValidTransaction(extrinsic, null));
    }

    public boolean existsInPool(Extrinsic extrinsic) {
        return transactionPool.exists(extrinsic);
    }

    public boolean shouldAddToQueue(ValidTransaction validTransaction) {
        Set<byte[]> provided = transactionQueue.stream()
                .flatMap(entry -> Arrays.stream(entry.getTransactionValidity().getProvides()))
                .collect(Collectors.toSet());

        Set<byte[]> required = Arrays.stream(validTransaction.getTransactionValidity().getRequires())
                .collect(Collectors.toSet());

        return ByteArrayUtils.sourceContainsAll(provided, required);
    }

    public void removeExtrinsic(Extrinsic extrinsic) {
        transactionPool.removeExtrinsic(extrinsic);
        ValidTransaction transactionToBeRemoved = new ValidTransaction(extrinsic, null);
        transactionQueue.remove(transactionToBeRemoved);
    }

    public void removeExtrinsicFromPool(Extrinsic extrinsic) {
        transactionPool.removeExtrinsic(extrinsic);
    }

    public byte[] addToPool(ValidTransaction validTransaction) {
        return transactionPool.insert(validTransaction);
    }
}
