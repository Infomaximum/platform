package com.infomaximum.platform.sdk.graphql.datafetcher.listener;

import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Слушатель исключений GraphQL-резолверов.
 * Получает события только для исключений платформы ({@code PlatformException},
 * пришедших в обёртке {@code PlatformRuntimeException}); прочие исключения
 * резолверов слушателю не передаются.
 * Вызывается до формирования GraphQL-ответа; выброшенное слушателем
 * {@code RuntimeException} подавляется и не влияет на ответ клиенту.
 */
public interface DataFetcherExceptionListener {

    /**
     * Обрабатывает исключение GraphQL-резолвера.
     *
     * @param event данные исключения и контекст исполнения поля
     */
    void onDataFetcherException(@NonNull DataFetcherExceptionEvent event);
}
