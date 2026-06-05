package com.infomaximum.platform.component.frontend.request.graphql.builder.impl;

import com.infomaximum.platform.component.frontend.engine.uploadfile.FrontendMultipartSource;
import com.infomaximum.platform.component.frontend.request.GRequestHttp;
import com.infomaximum.platform.component.frontend.request.graphql.GraphQLRequest;
import com.infomaximum.platform.component.frontend.request.graphql.builder.impl.attribute.GraphQLRequestAttributeBuilderEmpty;
import com.infomaximum.platform.exception.PlatformException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Тесты чтения HTTP-заголовка {@code X-Request-Id} в
 * {@link DefaultGraphQLRequestBuilder} и его проброса в построенный
 * {@link GRequestHttp}.
 */
class DefaultGraphQLRequestBuilderTest {

    /**
     * Заголовок {@code X-Request-Id} прочитан и виден через
     * {@link GRequestHttp#getXRequestId()} на построенном запросе.
     */
    @Test
    void readsXRequestIdHeaderIntoGRequestHttp() throws PlatformException {
        String requestId = UUID.randomUUID().toString();
        HttpServletRequest request = baseMockedRequest();
        when(request.getHeader("X-Request-Id")).thenReturn(requestId);

        DefaultGraphQLRequestBuilder builder = newBuilder();
        try (GraphQLRequest gqlRequest = builder.build(request)) {
            assertThat(gqlRequest.getGRequest()).isInstanceOf(GRequestHttp.class);
            GRequestHttp gRequestHttp = (GRequestHttp) gqlRequest.getGRequest();
            assertThat(gRequestHttp.getXRequestId()).isEqualTo(requestId);
        }
    }

    /**
     * Заголовок не передан клиентом — геттер возвращает {@code null}.
     */
    @Test
    void missingXRequestIdHeaderYieldsNull() throws PlatformException {
        HttpServletRequest request = baseMockedRequest();
        when(request.getHeader("X-Request-Id")).thenReturn(null);

        DefaultGraphQLRequestBuilder builder = newBuilder();
        try (GraphQLRequest gqlRequest = builder.build(request)) {
            GRequestHttp gRequestHttp = (GRequestHttp) gqlRequest.getGRequest();
            assertThat(gRequestHttp.getXRequestId()).isNull();
        }
    }

    /**
     * Заголовок {@code X-CSRF-Token} прочитан и виден через {@link GRequestHttp#getXCsrfToken()}.
     */
    @Test
    void readsXCsrfTokenHeaderIntoGRequestHttp() throws PlatformException {
        String csrfToken = UUID.randomUUID().toString();
        HttpServletRequest request = baseMockedRequest();
        when(request.getHeader("X-CSRF-Token")).thenReturn(csrfToken);

        DefaultGraphQLRequestBuilder builder = newBuilder();
        try (GraphQLRequest gqlRequest = builder.build(request)) {
            GRequestHttp gRequestHttp = (GRequestHttp) gqlRequest.getGRequest();
            assertThat(gRequestHttp.getXCsrfToken()).isEqualTo(csrfToken);
        }
    }

    /**
     * Заголовок {@code X-CSRF-Token} не передан — геттер возвращает {@code null}.
     */
    @Test
    void missingXCsrfTokenHeaderYieldsNull() throws PlatformException {
        HttpServletRequest request = baseMockedRequest();
        when(request.getHeader("X-CSRF-Token")).thenReturn(null);

        DefaultGraphQLRequestBuilder builder = newBuilder();
        try (GraphQLRequest gqlRequest = builder.build(request)) {
            GRequestHttp gRequestHttp = (GRequestHttp) gqlRequest.getGRequest();
            assertThat(gRequestHttp.getXCsrfToken()).isNull();
        }
    }

    private static HttpServletRequest baseMockedRequest() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader(anyString())).thenReturn(null);
        when(request.getIntHeader("X-Retry-Count")).thenReturn(-1);
        when(request.getParameter("query")).thenReturn("{ __typename }");
        when(request.getParameter("operationName")).thenReturn(null);
        when(request.getParameter("variables")).thenReturn(null);
        when(request.getParameterNames()).thenReturn(Collections.emptyEnumeration());
        when(request.getCookies()).thenReturn(null);
        return request;
    }

    private static DefaultGraphQLRequestBuilder newBuilder() {
        FrontendMultipartSource multipart = mock(FrontendMultipartSource.class);
        return new DefaultGraphQLRequestBuilder(multipart, new GraphQLRequestAttributeBuilderEmpty());
    }
}
