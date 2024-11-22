package com.infomaximum.platform.prometheus.http;

import com.infomaximum.network.event.HttpChannelListener;
import com.infomaximum.platform.prometheus.metric.HttpServerRequestsMetric;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.server.Request;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class PrometheusRequestListener implements HttpChannelListener, AutoCloseable {

    private final ConcurrentLinkedQueue<RequestInfo> queue;
    private final ExecutorService executorService;
    private final AtomicBoolean isClose = new AtomicBoolean(false);

    public PrometheusRequestListener(HttpServerRequestsMetric httpServerRequestsMetric) {
        this.queue = new ConcurrentLinkedQueue<>();
        this.executorService = Executors.newSingleThreadExecutor();
        executorService.submit(() -> {
            while (!isClose.get()) {
                try {
                    if (queue.isEmpty()) {
                        Thread.sleep(100L);
                    }
                    RequestInfo requestInfo;
                    while (!isClose.get() && (requestInfo = queue.poll()) != null) {
                        httpServerRequestsMetric.requestsSecondsMetric
                                .observeWithLabelValues(
                                        requestInfo.requestSeconds,
                                        requestInfo.method,
                                        String.valueOf(requestInfo.status),
                                        requestInfo.uriPath
                                );
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
    }

    @Override
    public void onComplete(Request request, int status, HttpFields headers, Throwable failure) {
        queue.add(new RequestInfo(
                request.getMethod(),
                request.getHttpURI().getPath(),
                status,
                ((double) System.nanoTime() - request.getBeginNanoTime()) / 1E9));
    }

    @Override
    public void close() {
        isClose.set(true);
        executorService.shutdownNow();
    }

    private record RequestInfo(String method, String uriPath, int status, double requestSeconds){}
}
