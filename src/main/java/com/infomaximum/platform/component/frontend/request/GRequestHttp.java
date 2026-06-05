package com.infomaximum.platform.component.frontend.request;

import com.infomaximum.cluster.core.remote.struct.RemoteObject;
import com.infomaximum.cluster.graphql.struct.GRequest;
import jakarta.servlet.http.Cookie;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.io.Serializable;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GRequestHttp extends GRequest {

    private final HashMap<String, String[]> parameters;
    private final HashMap<String, String[]> attributes;

    private final Cookie[] cookies;

    private final ArrayList<UploadFile> uploadFiles;

    private String idempotencyKey;
    private Integer xRetryCount;
    private String xRequestId;
    private String xCsrfToken;

    /**
     * Слот «намерения ответа»: значения {@code Set-Cookie} и дополнительные заголовки,
     * которые обработка запроса просит добавить в HTTP-ответ.
     * */
    private ArrayList<String> responseSetCookies;
    private LinkedHashMap<String, String> responseHeaders;

    @Deprecated
    public GRequestHttp(Instant instant, RemoteAddress remoteAddress, String query, HashMap<String, Serializable> queryVariables, String operationName, String xTraceId, HashMap<String, String[]> parameters, HashMap<String, String[]> attributes, Cookie[] cookies, ArrayList<UploadFile> uploadFiles) {
        super(instant, remoteAddress, query, queryVariables, operationName, xTraceId);

        this.parameters = parameters;

        this.attributes = attributes;

        this.cookies = cookies;

        this.uploadFiles = uploadFiles;
    }

    public GRequestHttp(Builder builder) {
        super(builder.instant, builder.remoteAddress, builder.query, builder.queryVariables, builder.operationName, builder.xTraceId);

        this.parameters = builder.parameters;
        this.attributes = builder.attributes;
        this.cookies = builder.cookies;
        this.uploadFiles = builder.uploadFiles;
        this.idempotencyKey = builder.idempotencyKey;
        this.xRetryCount = builder.xRetryCount;
        this.xRequestId = builder.xRequestId;
        this.xCsrfToken = builder.xCsrfToken;
    }

    public String getParameter(String name) {
        String[] values = getParameters(name);
        return (values == null) ? null : values[0];
    }

    public String[] getParameters(String name) {
        return parameters.get(name);
    }

    public String[] getAttributes(String name) {
        if (attributes == null) {
            return null;
        }
        return attributes.get(name);
    }

    public Cookie getCookie(String name) {
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (name.equals(cookie.getName())) return cookie;
            }
        }
        return null;
    }

    public HashMap<String, String[]> getParameters() {
        return parameters;
    }

    public HashMap<String, String[]> getAttributes() {
        return attributes;
    }

    public Cookie[] getCookies() {
        return cookies;
    }

    public ArrayList<UploadFile> getUploadFiles() {
        return uploadFiles;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Integer getXRetryCount() {
        return xRetryCount;
    }

    /**
     * Возвращает идентификатор пользовательского запроса из HTTP-заголовка
     * {@code X-Request-Id}. Значение сохраняется неизменным между повторными
     * HTTP-вызовами (retry) от того же клиента; используется для трассировки
     * цепочки retry в серверных логах.
     *
     * @return значение заголовка {@code X-Request-Id} либо {@code null}, если
     * клиент его не передал.
     */
    public @Nullable String getXRequestId() {
        return xRequestId;
    }

    /**
     * Возвращает значение HTTP-заголовка {@code X-CSRF-Token} из запроса.
     *
     * @return значение заголовка {@code X-CSRF-Token} либо {@code null}, если клиент его не передал.
     */
    public @Nullable String getXCsrfToken() {
        return xCsrfToken;
    }

    /**
     * Добавляет в ответ готовое значение заголовка {@code Set-Cookie}.
     *
     * @param setCookieValue значение заголовка {@code Set-Cookie}.
     */
    public void addResponseSetCookie(@NonNull String setCookieValue) {
        if (responseSetCookies == null) {
            responseSetCookies = new ArrayList<>(2);
        }
        responseSetCookies.add(setCookieValue);
    }

    /**
     * Добавляет в ответ дополнительный заголовок (например, {@code X-CSRF-Token}). Повторный
     * вызов с тем же именем перезаписывает значение.
     *
     * @param name  имя заголовка.
     * @param value значение заголовка.
     */
    public void addResponseHeader(@NonNull String name, @NonNull String value) {
        if (responseHeaders == null) {
            responseHeaders = new LinkedHashMap<>();
        }
        responseHeaders.put(name, value);
    }

    /**
     * Возвращает накопленные значения {@code Set-Cookie} для переноса в ответ.
     *
     * @return список значений заголовка либо {@code null}, если ничего не добавлено.
     */
    public @Nullable List<String> getResponseSetCookies() {
        return responseSetCookies == null ? null : Collections.unmodifiableList(responseSetCookies);
    }

    /**
     * Возвращает накопленные дополнительные заголовки ответа.
     *
     * @return отображение «имя → значение» либо {@code null}, если ничего не добавлено.
     */
    public @Nullable Map<String, String> getResponseHeaders() {
        return responseHeaders == null ? null : Collections.unmodifiableMap(responseHeaders);
    }

    public static class UploadFile implements RemoteObject {

        public final String fieldname;
        public final String filename;
        public final String contentType;
        public final URI uri;
        public final long size;

        public UploadFile(String fieldname, String filename, String contentType, URI uri, long size) {
            this.fieldname = fieldname;
            this.filename = filename;
            this.contentType = contentType;
            this.uri = uri;
            this.size = size;
        }
    }

    public static class Builder {

        private Instant instant;
        private RemoteAddress remoteAddress;
        private String query;
        private HashMap<String, Serializable> queryVariables;
        private String operationName;
        private String xTraceId;

        private Integer xRetryCount;
        private String idempotencyKey;
        private String xRequestId;
        private String xCsrfToken;
        private HashMap<String, String[]> parameters;
        private HashMap<String, String[]> attributes;
        private Cookie[] cookies;
        private ArrayList<UploadFile> uploadFiles;

        public Builder withInstantRequest(Instant instant) {
            this.instant = instant;
            return this;
        }

        public Builder withRemoteAddress(RemoteAddress remoteAddress) {
            this.remoteAddress = remoteAddress;
            return this;
        }

        public Builder withQuery(String query) {
            this.query = query;
            return this;
        }

        public Builder withQueryVariables(HashMap<String, Serializable> queryVariables) {
            this.queryVariables = queryVariables;
            return this;
        }

        public Builder withOperationName(String operationName) {
            this.operationName = operationName;
            return this;
        }

        public Builder withXTraceId(String xTraceId) {
            this.xTraceId = xTraceId;
            return this;
        }

        public Builder withXRetryCount(Integer xRetryCount) {
            this.xRetryCount = xRetryCount;
            return this;
        }

        public GRequestHttp.Builder withIdempotencyKey(String idempotencyKey) {
            this.idempotencyKey = idempotencyKey;
            return this;
        }

        /**
         * Запоминает идентификатор пользовательского запроса из HTTP-заголовка
         * {@code X-Request-Id} для последующего логирования.
         *
         * @param xRequestId значение заголовка либо {@code null}, если заголовка нет.
         * @return текущий билдер.
         */
        public Builder withXRequestId(@Nullable String xRequestId) {
            this.xRequestId = xRequestId;
            return this;
        }

        /**
         * Запоминает значение HTTP-заголовка {@code X-CSRF-Token} из запроса для последующей
         * проверки защиты от CSRF.
         *
         * @param xCsrfToken значение заголовка либо {@code null}, если заголовка нет.
         * @return текущий билдер.
         */
        public Builder withXCsrfToken(@Nullable String xCsrfToken) {
            this.xCsrfToken = xCsrfToken;
            return this;
        }

        public GRequestHttp.Builder withParameters(HashMap<String, String[]> parameters) {
            this.parameters = parameters;
            return this;
        }

        public Builder withAttributes(HashMap<String, String[]> attributes) {
            this.attributes = attributes;
            return this;
        }

        public Builder withCookies(Cookie[] cookies) {
            this.cookies = cookies;
            return this;
        }

        public Builder withUploadFiles(ArrayList<UploadFile> uploadFiles) {
            this.uploadFiles = uploadFiles;
            return this;
        }

        public GRequestHttp build() {
            return new GRequestHttp(this);
        }
    }
}
