package com.infomaximum.platform.sdk.graphql.datafetcher.listener;

/**
 * Тип GraphQL-операции запроса.
 */
public enum OperationType {

    /** Чтение данных. */
    QUERY,

    /** Изменение данных. */
    MUTATION,

    /** Подписка на изменения. */
    SUBSCRIPTION
}
