package com.infomaximum.platform.utils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

public class TimedCache<K, V> {

    private final ConcurrentHashMap<K, CacheEntry<V>> storage;
    private final ReentrantLock lock;
    private final long TTL_MS;
    private final AtomicLong counter = new AtomicLong(0);

    public TimedCache(long ttlMs) {
        this.storage = new ConcurrentHashMap<>();
        this.lock = new ReentrantLock();
        this.TTL_MS = ttlMs;
    }

    public void put(K key, V value) {
        put(key, value, TTL_MS);
    }

    public void put(K key, V value, long duration, TimeUnit unit) {
        long ttlMs = unit.toMillis(duration);
        put(key, value, ttlMs);
    }

    public void put(K key, V value, long ttlMs) {
        long expirationTime = System.currentTimeMillis() + ttlMs;
        storage.put(key, new CacheEntry<>(value, expirationTime));
        if (counter.incrementAndGet() >= 100) {
            removeExpired();
        }
    }

    public V get(K key) {
        CacheEntry<V> entry = storage.get(key);
        if (entry == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        if (entry.isExpired(now)) {
            removeExpired();
            return null;
        }
        return entry.value;
    }

    public boolean containsKey(K key) {
        V value = get(key);
        return value != null;
    }

    public void remove(K key) {
        storage.remove(key);
    }

    public int size() {
        return storage.size();
    }

    private void removeExpired() {
        try (LockGuard guard = new LockGuard(lock)) {
            counter.set(0);
            if (storage.isEmpty()) {
                return;
            }
            long now = System.currentTimeMillis();
            storage.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
        }
    }

    private record CacheEntry<V>(V value, long expirationTime) {

        boolean isExpired(long currentTime) {
            return currentTime > expirationTime;
        }
    }
}
