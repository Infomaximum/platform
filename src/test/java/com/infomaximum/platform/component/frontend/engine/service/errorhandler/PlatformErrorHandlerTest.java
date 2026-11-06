package com.infomaximum.platform.component.frontend.engine.service.errorhandler;

import org.eclipse.jetty.server.ConnectionMetaData;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Тесты {@link PlatformErrorHandler}: непредусмотренная ошибка запроса роняет процесс
 * ({@code uncaughtExceptionHandler}) во всех состояниях HTTP-сервера, кроме его остановки.
 * Состояния настоящие: обработчик вызывается изнутри {@link Server#doStart()} и {@link Server#doStop()}.
 */
public class PlatformErrorHandlerTest {

    /** При остановке Jetty рвёт соединения с запросами в обработке: 500 без исключения — не авария. */
    @Test
    public void doesNotCrashOnBrokenRequestWhileServerStopping() throws Exception {
        LifecycleServer server = new LifecycleServer();
        List<Throwable> crashes = new ArrayList<>();
        server.onStopping = () -> handle(server, crashes, null);
        server.start();
        server.stop();

        assertThat(crashes).isEmpty();
    }

    /** При остановке не роняет процесс и ошибка обработки запроса (NPE в уже остановленном компоненте). */
    @Test
    public void doesNotCrashOnRequestFailureWhileServerStopping() throws Exception {
        LifecycleServer server = new LifecycleServer();
        List<Throwable> crashes = new ArrayList<>();
        server.onStopping = () -> handle(server, crashes, new RuntimeException(new NullPointerException()));
        server.start();
        server.stop();

        assertThat(crashes).isEmpty();
    }

    /** В работе поведение прежнее: непредусмотренная ошибка роняет процесс. */
    @Test
    public void crashesOnUnknownErrorWhileServerStarted() throws Exception {
        LifecycleServer server = new LifecycleServer();
        List<Throwable> crashes = new ArrayList<>();
        server.start();
        try {
            handle(server, crashes, null);
            handle(server, crashes, new RuntimeException(new NullPointerException()));
        } finally {
            server.stop();
        }

        assertThat(crashes).hasSize(2);
    }

    /** При старте поведение прежнее: непредусмотренная ошибка роняет процесс. */
    @Test
    public void crashesOnUnknownErrorWhileServerStarting() throws Exception {
        LifecycleServer server = new LifecycleServer();
        List<Throwable> crashes = new ArrayList<>();
        server.onStarting = () -> handle(server, crashes, null);
        server.start();
        server.stop();

        assertThat(crashes).hasSize(1);
    }

    private static void handle(Server server, List<Throwable> crashes, Throwable throwable) {
        Connector connector = mock(Connector.class);
        when(connector.getServer()).thenReturn(server);
        ConnectionMetaData connectionMetaData = mock(ConnectionMetaData.class);
        when(connectionMetaData.getConnector()).thenReturn(connector);
        Request request = mock(Request.class);
        when(request.getConnectionMetaData()).thenReturn(connectionMetaData);
        Response response = mock(Response.class);
        when(response.getStatus()).thenReturn(500);

        new PlatformErrorHandler(null, (thread, e) -> crashes.add(e))
                .handle(request, response, null, throwable);
    }

    private static class LifecycleServer extends Server {

        private Runnable onStarting = () -> {};
        private Runnable onStopping = () -> {};

        @Override
        protected void doStart() throws Exception {
            onStarting.run();
            super.doStart();
        }

        @Override
        protected void doStop() throws Exception {
            onStopping.run();
            super.doStop();
        }
    }
}
