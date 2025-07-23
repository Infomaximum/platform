package com.infomaximum.platform.querypool;

import com.infomaximum.platform.exception.PlatformException;
import com.infomaximum.platform.querypool.service.DetectHighLoad;
import com.infomaximum.platform.querypool.service.DetectLongQuery;
import com.infomaximum.platform.querypool.service.DetectQueueFilling;
import com.infomaximum.platform.querypool.service.threadcontext.ThreadContext;
import com.infomaximum.platform.querypool.service.threadcontext.ThreadContextImpl;
import com.infomaximum.platform.querypool.thread.QueryThreadPoolExecutor;
import com.infomaximum.platform.sdk.component.Component;
import com.infomaximum.platform.sdk.context.ContextTransaction;
import com.infomaximum.platform.sdk.context.ContextTransactionInternal;
import com.infomaximum.platform.sdk.exception.GeneralExceptionBuilder;
import com.infomaximum.platform.utils.DefaultThreadGroup;
import com.infomaximum.platform.utils.DefaultThreadPoolExecutor;
import com.infomaximum.platform.utils.LockGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

public class QueryPool {

    private static final Logger logger = LoggerFactory.getLogger(QueryPool.class);

    public enum LockType {
        SHARED, EXCLUSIVE
    }

    public enum Priority {
        LOW, HIGH
    }

    @FunctionalInterface
    public interface Callback {

        void execute(QueryPool pool);
    }

    public static class QueryWrapper<T> {

        final QueryPool queryPool;
        final Component component;
        final ContextTransaction context;
        final Query<T> query;
        final QueryFuture<T> future;

        final Map<String, LockType> resources;

        private volatile Thread thread;
        private volatile Instant timeStart;
        private volatile Instant timeComplete;

        QueryWrapper(QueryPool queryPool, Component component, ContextTransaction context, Query<T> query) throws PlatformException {
            this(new QueryFuture<>(queryPool, component, context, new CompletableFuture<>()), query);
        }

        QueryWrapper(QueryFuture<T> queryFuture, Query<T> query) throws PlatformException {
            this.queryPool = queryFuture.queryPool;
            this.component = queryFuture.component;
            this.context = queryFuture.context;
            this.query = query;
            this.future = queryFuture;

            try (ResourceProviderImpl provider = new ResourceProviderImpl(component)) {
                query.prepare(provider);
                this.resources = provider.getResources();
            }
        }

        public ContextTransaction getContext() {
            return context;
        }

        public Thread getThread() {
            return thread;
        }

        public Instant getTimeStart() {
            return timeStart;
        }

        public Instant getTimeComplete() {
            return timeComplete;
        }

        public Map<String, LockType> getResources() {
            return Collections.unmodifiableMap(resources);
        }

        private void execute() {
            timeStart = Instant.now();
            thread = Thread.currentThread();
            try (QueryTransaction transaction = new QueryTransaction(component.getDomainObjectSource())) {
                try {
                    ((ContextTransactionInternal) context).setTransaction(transaction);
                    queryPool.threadContext.setContext(context);

                    T result = query.execute(transaction);
                    transaction.commit();
                    transaction.fireCommitListeners();
                    future.complete(result);
                } catch (PlatformException e) {
                    transaction.fireRollbackListeners(e);
                    future.completeExceptionally(e);
                }
            } catch (Throwable e) {
                future.completeExceptionally(e);
                throw e;
            } finally {
                timeComplete = Instant.now();
                queryPool.detectQueueFilling.queryComplete(this, timeStart, timeComplete);
                thread = null;
                queryPool.threadContext.clearContext();
            }
        }
    }

    private static class QueryLockType {

        final QueryWrapper query;
        final LockType lockType;

        QueryLockType(QueryWrapper query, LockType lockType) {
            this.query = query;
            this.lockType = lockType;
        }
    }

    private static class ResourceMap extends HashMap<String, ArrayList<QueryLockType>> {
    }

    //TODO Удалить костыль
    public static final int MAX_THREAD_COUNT = Runtime.getRuntime().availableProcessors() * 80;
    public static final int MAX_WORKED_QUERY_COUNT = MAX_THREAD_COUNT * 5;

    /**
     * Пользовательские запросы более приоритетны, потоэму лучше их положить в очередь, чем сразу кинуть ошибку
     */
    public static final int MAX_WAITING_HIGH_QUERY_COUNT = MAX_THREAD_COUNT * 20;
    /**
     * Очередь для низко приоритетных запросов в четыре раза меньше пула потоков данных, сделана для того,
     * что бы сервер не захлебывался в моменты пиковой нагрузки
     */
    public static final int MAX_WAITING_LOW_QUERY_COUNT = MAX_THREAD_COUNT / 4;

    private final ThreadPoolExecutor threadPool;

    private final ReentrantLock lock = new ReentrantLock();
    private final ArrayList<String> maintenanceMarkers = new ArrayList<>();
    private final ResourceMap occupiedResources = new ResourceMap();
    private final ResourceMap waitingResources = new ResourceMap();
    private final ArrayList<Callback> emptyPoolListeners = new ArrayList<>();

    private final DetectLongQuery detectLongQuery;
    private final DetectHighLoad detectHighLoad;
    private final DetectQueueFilling detectQueueFilling;
    private final ThreadContextImpl threadContext;

    private volatile int highPriorityWaitingQueryCount = 0;
    private volatile int lowPriorityWaitingQueryCount = 0;
    private volatile PlatformException hardException = null;

    private volatile Instant logLastTime = Instant.now();

    public QueryPool(boolean isVirtualThread, Thread.UncaughtExceptionHandler uncaughtExceptionHandler) {
        if (isVirtualThread) {
            this.threadPool = new QueryThreadPoolExecutor(
                    MAX_THREAD_COUNT,
                    MAX_THREAD_COUNT,
                    0L,
                    TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(MAX_WORKED_QUERY_COUNT),
                    uncaughtExceptionHandler
            );
        } else {
            DefaultThreadGroup defaultThreadGroup = new DefaultThreadGroup("QueryPool", uncaughtExceptionHandler);
            this.threadPool = new DefaultThreadPoolExecutor(
                    MAX_THREAD_COUNT,
                    MAX_THREAD_COUNT,
                    0L,
                    TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(MAX_WORKED_QUERY_COUNT),
                    defaultThreadGroup
            );
        }

        this.detectLongQuery = new DetectLongQuery(this, uncaughtExceptionHandler);
        this.detectHighLoad = new DetectHighLoad(this, threadPool, uncaughtExceptionHandler);
        this.detectQueueFilling = new DetectQueueFilling();
        this.threadContext = new ThreadContextImpl();
    }

    public void setHardException(PlatformException e) {
        hardException = e;
    }

    protected <T> void execute(QueryFuture<T> queryFuture, Query<T> query, boolean failIfPoolBusy) {
        QueryWrapper<T> queryWrapp;
        try {
            queryWrapp = new QueryWrapper<>(queryFuture, query);
        } catch (PlatformException e) {
            queryFuture.completeExceptionally(e);
            return;
        }
        execute(queryWrapp, failIfPoolBusy);
    }

    public <T> QueryFuture<T> execute(Component component, Query<T> query) {
        return execute(component, null, query, true);
    }

    public <T> QueryFuture<T> execute(Component component, ContextTransaction context, Query<T> query) {
        return execute(component, context, query, true);
    }

    public <T> QueryFuture<T> execute(Component component, ContextTransaction context, Query<T> query, boolean failIfPoolBusy) {
        QueryWrapper<T> queryWrapper;
        try {
            queryWrapper = new QueryWrapper<>(this, component, context, query);
        } catch (PlatformException e) {
            CompletableFuture<T> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return new QueryFuture<>(this, component, context, future);
        }
        return execute(queryWrapper, failIfPoolBusy);
    }

    protected <T> QueryFuture<T> execute(QueryWrapper<T> queryWrapper, boolean failIfPoolBusy) {
        PlatformException localHardException = hardException;
        if (localHardException != null) {
            queryWrapper.future.completeExceptionally(localHardException);
            return queryWrapper.future;
        }

        try (LockGuard guard = new LockGuard(lock)) {
            if (failIfPoolBusy && isOverloaded(queryWrapper.query.getPriority())) {
                queryWrapper.future.completeExceptionally(createOverloadedException());
            } else if (isOccupiedResources(queryWrapper.resources)) {
                if (failIfPoolBusy && isMaintenance()) {
                    queryWrapper.future.completeExceptionally(createMaintenanceException());
                } else {
                    captureWaitingResources(queryWrapper);
                }
            } else {
                submitQuery(queryWrapper);
            }
        }

        return queryWrapper.future;
    }

    public boolean isBusyFor(Priority priority) {
        try (LockGuard guard = new LockGuard(lock)) {
            return isMaintenance() || isOverloaded(priority);
        }
    }

    /**
     * @return null if query is not submitted
     */
    public <T> QueryFuture<T> tryExecuteImmediately(Component component, Query<T> query) throws PlatformException {
        return tryExecuteImmediately(component, null, query);
    }

    /**
     * @return null if query is not submitted
     */
    public <T> QueryFuture<T> tryExecuteImmediately(Component component, ContextTransaction context, Query<T> query) throws PlatformException {
        if (hardException != null) {
            return null;
        }

        QueryWrapper<T> queryWrapper = new QueryWrapper<>(this, component, context, query);

        try (LockGuard guard = new LockGuard(lock)) {
            if (isOverloaded(queryWrapper.query.getPriority()) || isOccupiedResources(queryWrapper.resources)) {
                return null;
            }
            submitQuery(queryWrapper);
            return queryWrapper.future;
        }
    }

    public void addEmptyReachedListner(Callback callback) {
        try (LockGuard guard = new LockGuard(lock)) {
            emptyPoolListeners.add(callback);
        }
    }

    public void removeEmptyReachedListner(Callback callback) {
        try (LockGuard guard = new LockGuard(lock)) {
            emptyPoolListeners.remove(callback);
        }
    }

    public void tryFireEmptyReachedListener() {
        Callback[] listeners;
        try (LockGuard guard = new LockGuard(lock)) {
            listeners = getFiringEmptyPoolListners();
        }
        fireEmptyPoolListeners(listeners);
    }

    public boolean waitingQueryExists(Priority priority) {
        return switch (priority) {
            case LOW -> lowPriorityWaitingQueryCount != 0;
            case HIGH -> highPriorityWaitingQueryCount != 0;
        };
    }

    public Collection<QueryWrapper> getExecuteQueries() {
        final HashSet<QueryWrapper> queries = new HashSet<>();
        try (LockGuard guard = new LockGuard(lock)) {
            occupiedResources.forEach((key, value) -> value.forEach(item -> queries.add(item.query)));
        }
        return queries;
    }

    public ThreadContext getThreadContext(){
        return threadContext;
    }

    public void shutdownAwait() throws InterruptedException {
        detectLongQuery.shutdownAwait();
        detectHighLoad.shutdownAwait();

        threadPool.shutdown();

        final HashSet<QueryWrapper> queries = new HashSet<>();
        try (LockGuard guard = new LockGuard(lock)) {
            waitingResources.forEach((key, value) -> value.forEach(item -> queries.add(item.query)));
            waitingResources.clear();
        }
        queries.forEach((query) -> query.future.completeExceptionally(GeneralExceptionBuilder.buildServerShutsDownException()));

        threadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
    }

    public boolean isShutdown() {
        return threadPool.isShutdown();
    }

    public void await() throws InterruptedException {
        while (true) {
            try (LockGuard guard = new LockGuard(lock)) {
                if (waitingResources.isEmpty()) {
                    break;
                }
            }

            Thread.sleep(500L);
        }

        shutdownAwait();
    }

    private void submitQuery(QueryWrapper<?> queryWrapp) {
        captureOccupiedResources(queryWrapp);

        try {
            threadPool.submit(() -> {
                try {
                    queryWrapp.execute();
                } catch (Throwable e) {
                    try (LockGuard guard = new LockGuard(lock)) {
                        releaseOccupiedResources(queryWrapp);
                    } catch (Throwable ignore) {
                        // do nothing
                    }

                    throw e;
                }

                Callback[] emptyListners;
                try (LockGuard guard = new LockGuard(lock)) {
                    releaseOccupiedResources(queryWrapp);
                    emptyListners = getFiringEmptyPoolListners();

                    //COMMENT Миронов В. можно оптимизировать поиск запросов на исполнение если releaseResources будет
                    // возвращать список ресурсов у которых нет активных Query или он был заблокирован на SHARED
                    trySubmitNextAvailableQueryBy(queryWrapp.resources);
                }

                fireEmptyPoolListeners(emptyListners);
            });
        } catch (RejectedExecutionException e) {
            releaseOccupiedResources(queryWrapp);
            queryWrapp.future.completeExceptionally(GeneralExceptionBuilder.buildServerShutsDownException());
        } catch (Throwable e) {
            releaseOccupiedResources(queryWrapp);
            throw e;
        }
    }

    private Callback[] getFiringEmptyPoolListners() {
        if (!occupiedResources.isEmpty() || !waitingResources.isEmpty() || emptyPoolListeners.isEmpty()) {
            return null;
        }

        Callback[] listners = new Callback[emptyPoolListeners.size()];
        emptyPoolListeners.toArray(listners);
        return listners;
    }

    private void fireEmptyPoolListeners(Callback[] listeners) {
        if (listeners != null) {
            for (Callback item : listeners) {
                item.execute(this);
            }
        }
    }

    private void captureOccupiedResources(QueryWrapper queryWrapper) {
        appendResources(queryWrapper, occupiedResources);
        pushMaintenance(queryWrapper.query.getMaintenanceMarker());
    }

    private void releaseOccupiedResources(QueryWrapper queryWrapper) {
        popMaintenance(queryWrapper.query.getMaintenanceMarker());
        removeResources(queryWrapper, occupiedResources);
    }

    private void captureWaitingResources(QueryWrapper queryWrapper) {
        switch (queryWrapper.query.getPriority()) {
            case LOW -> ++lowPriorityWaitingQueryCount;
            case HIGH -> ++highPriorityWaitingQueryCount;
        }
        appendResources(queryWrapper, waitingResources);
    }

    private void releaseWaitingResources(QueryWrapper queryWrapper) {
        removeResources(queryWrapper, waitingResources);
        switch (queryWrapper.query.getPriority()) {
            case LOW -> --lowPriorityWaitingQueryCount;
            case HIGH -> --highPriorityWaitingQueryCount;
        }
    }

    private void trySubmitNextAvailableQueryBy(Map<String, LockType> releasedResources) {
        HashSet<QueryWrapper> candidates = new HashSet<>();

        for (Map.Entry<String, LockType> res : releasedResources.entrySet()) {
            ArrayList<QueryLockType> value = waitingResources.get(res.getKey());
            if (value != null) {
                value.forEach(item -> candidates.add(item.query));
            }
        }

        for (QueryWrapper<?> query : candidates) {
            if (isOccupiedResources(query.resources)) {
                continue;
            }

            if (isFilledThreadPool()) {
                break;
            }

            releaseWaitingResources(query);
            submitQuery(query);
        }
    }

    private boolean isOverloaded(Priority newQueryPriority) {
        if (isFilledThreadPool()) {
            return true;
        }

        return switch (newQueryPriority) {
            case LOW ->
                //В случа низкоприоритетных запросов смотрим так же и на очередь высокоприоритетных - и если там растет нагрузка,
                // то низкоприоритетные мы сразу откидываем
                    (
                            //Аналог highPriorityWaitingQueryCount >= (MAX_WAITING_HIGH_QUERY_COUNT/4)
                            (highPriorityWaitingQueryCount >= (MAX_WAITING_HIGH_QUERY_COUNT >> 2)) ||
                                    (lowPriorityWaitingQueryCount >= MAX_WAITING_LOW_QUERY_COUNT)
                    );
            case HIGH -> highPriorityWaitingQueryCount >= MAX_WAITING_HIGH_QUERY_COUNT;
        };
    }

    private boolean isFilledThreadPool() {
        return threadPool.getQueue().size() >= MAX_WORKED_QUERY_COUNT;
    }

    private void pushMaintenance(String marker) {
        if (marker != null) {
            maintenanceMarkers.add(marker);
        }
    }

    private void popMaintenance(String marker) {
        if (marker != null) {
            maintenanceMarkers.removeLast();
        }
    }

    private boolean isMaintenance() {
        return !maintenanceMarkers.isEmpty();
    }

    private boolean isOccupiedResources(final Map<String, LockType> targetResources) {
        for (Map.Entry<String, LockType> res : targetResources.entrySet()) {
            ArrayList<QueryLockType> foundValue = occupiedResources.get(res.getKey());
            if (foundValue == null || foundValue.isEmpty()) {
                continue;
            }

            if (res.getValue() == LockType.EXCLUSIVE || foundValue.getFirst().lockType == LockType.EXCLUSIVE) {
                return true;
            }
        }
        return false;
    }

    public int getHighPriorityWaitingQueryCount() {
        return highPriorityWaitingQueryCount;
    }

    public int getLowPriorityWaitingQueryCount() {
        return lowPriorityWaitingQueryCount;
    }

    private static void appendResources(QueryWrapper<?> query, ResourceMap destination) {
        for (Map.Entry<String, LockType> entry : query.resources.entrySet()) {
            destination.computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                    .add(new QueryLockType(query, entry.getValue()));
        }
    }

    private static void removeResources(QueryWrapper<?> query, ResourceMap destination) {
        for (Map.Entry<String, LockType> entry : query.resources.entrySet()) {
            ArrayList<QueryLockType> foundValue = destination.get(entry.getKey());
            if (foundValue == null) {
                continue;
            }

            foundValue.removeIf(item -> item.query == query);
            if (foundValue.isEmpty()) {
                destination.remove(entry.getKey());
            }
        }
    }

    private PlatformException createMaintenanceException() {
        return GeneralExceptionBuilder.buildServerBusyException(maintenanceMarkers.getLast());
    }

    private PlatformException createOverloadedException() {
        if (isMaintenance()) {
            return createMaintenanceException();
        }
        writeStateToLog();
        return GeneralExceptionBuilder.buildServerOverloadedException();
    }

    private void writeStateToLog() {
        Duration period = Duration.ofMinutes(5);
        Instant currentTime = Instant.now();
        if (currentTime.minus(period).isAfter(logLastTime)) {
            logLastTime = Instant.now();
            StringBuilder builder = new StringBuilder();
            builder.append("threadPoolActiveCount: ")
                    .append(threadPool.getActiveCount())
                    .append(" | threadPoolQueueSize: ")
                    .append(threadPool.getQueue().size())
                    .append(" | lowPriorityWaitingQueryCount: ")
                    .append(lowPriorityWaitingQueryCount)
                    .append(" | highPriorityWaitingQueryCount: ")
                    .append(highPriorityWaitingQueryCount);
            builder.append("\n occupiedResources: ");
            writeResourceMapTo(builder, occupiedResources);
            builder.append("\n waitingResources: ");
            writeResourceMapTo(builder, waitingResources);
            logger.debug(builder.toString());
        }
    }

    private void writeResourceMapTo(StringBuilder destination, ResourceMap resourceMap) {
        for (Map.Entry<String, ArrayList<QueryLockType>> entry : resourceMap.entrySet()) {
            int sharedCount = 0;
            int exclusiveCount = 0;
            for (QueryLockType query : entry.getValue()) {
                switch (query.lockType) {
                    case SHARED -> sharedCount++;
                    case EXCLUSIVE -> exclusiveCount++;
                }
            }
            destination.append('\n')
                    .append("resource: ")
                    .append(entry.getKey())
                    .append(" | exclusiveCount: ")
                    .append(exclusiveCount)
                    .append(" | sharedCount: ")
                    .append(sharedCount);
        }
    }
}
