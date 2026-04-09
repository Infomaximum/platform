package com.infomaximum.platform.service;

import com.infomaximum.cluster.Node;
import com.infomaximum.platform.Platform;
import com.infomaximum.platform.sdk.component.Component;
import com.infomaximum.platform.sdk.component.ComponentEvent;

import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

public class ComponentEventQueue {

    private final Platform platform;
    private final Component component;
    private final ExecutorService executorsVirtualThreads;
    private final ReentrantLock lock;
    private final LinkedList<Event> queue;

    public ComponentEventQueue(Platform platform, Component component) {
        this.platform = platform;
        this.component = component;
        this.executorsVirtualThreads = Executors.newSingleThreadExecutor(Thread.ofVirtual().factory());
        this.lock = new ReentrantLock();
        this.queue = new LinkedList<>();
    }

    public void pushEvent(Event event) {
        if (component.isStarted()) {
            push(event.eventRunnable);
            return;
        }
        lock.lock();
        try {
            if (component.isStarted()) {
                push(event.eventRunnable);
            } else {
                addWithOptimize(event);
            }
        } finally {
            lock.unlock();
        }
    }

    public boolean executeAll() {
        if (!component.isStarted()) {
            return false;
        }
        lock.lock();
        try {
            while (!queue.isEmpty()) {
                Event event = queue.pollFirst();
                push(event.eventRunnable);
            }
        } finally {
            lock.unlock();
        }
        return true;
    }

    private void push(Runnable eventRunnable) {
        executorsVirtualThreads.execute(() -> {
            try {
                eventRunnable.run();
            } catch (Throwable e) {
                platform.getUncaughtExceptionHandler().uncaughtException(Thread.currentThread(), e);
            }
        });
    }

    public void shutdown() {
        executorsVirtualThreads.shutdown();
    }

    private void addWithOptimize(Event event) {
        if (ComponentEvent.METHOD_ON_EVENT_DISCONNECT.equals(event.methodName())) {
            //Если произошло отключение ноды, то все события, связанные с ней, можно удалить из очереди.
            queue.removeIf(otherEvent -> event.node.getRuntimeId().equals(otherEvent.node.getRuntimeId()));
            return;
        }
        queue.addLast(event);
    }

    public record Event(String methodName, Node node, Runnable eventRunnable){}
}
