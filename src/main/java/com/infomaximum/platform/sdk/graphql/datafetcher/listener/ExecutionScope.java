package com.infomaximum.platform.sdk.graphql.datafetcher.listener;

import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.function.Function;

/**
 * Хранилище со временем жизни одного GraphQL-запроса.
 * Позволяет слушателю накапливать состояние между событиями одного запроса
 * (например, для дедупликации записей); потокобезопасно.
 */
public interface ExecutionScope {

    /**
     * Возвращает значение по ключу, при отсутствии — атомарно вычисляет и сохраняет.
     *
     * @param key ключ значения
     * @param mapping функция вычисления значения по ключу
     * @return существующее или вычисленное значение
     * @param <T> тип значения
     */
    <T> T computeIfAbsent(@NonNull Object key, @NonNull Function<Object, T> mapping);
}
