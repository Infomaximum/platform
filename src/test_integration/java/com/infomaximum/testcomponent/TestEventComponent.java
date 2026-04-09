package com.infomaximum.testcomponent;

import com.infomaximum.cluster.Node;
import com.infomaximum.cluster.core.component.RuntimeComponentInfo;
import com.infomaximum.cluster.event.CauseNodeDisconnect;
import com.infomaximum.platform.Platform;
import com.infomaximum.platform.sdk.component.Component;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.infomaximum.platform.service.ComponentEventQueue.*;

public class TestEventComponent extends Component {

    private volatile boolean isStarted = false;
    private final CopyOnWriteArrayList<Event> executeList = new CopyOnWriteArrayList<>();
    private final Platform platform;
    private volatile CountDownLatch latch = new CountDownLatch(0);

    public TestEventComponent(Platform platform) {
        this.platform = platform;
    }

    public void expectEvents(int count) {
        this.latch = new CountDownLatch(count);
    }

    public void awaitExpectedEvents() throws InterruptedException {
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("Timeout: события не были обработаны за 5 секунд");
        }
    }

    @Override
    public void onStarted() {
        isStarted = true;
        getComponentEventQueue(platform).executeAll();
    }

    @Override
    public boolean isStarted() {
        return isStarted;
    }

    @Override
    public void onEventConnect(Node node) {
        executeList.add(new Event(METHOD_ON_EVENT_CONNECT, node, null));
        latch.countDown();
    }

    @Override
    public void onEventDisconnect(Node node, CauseNodeDisconnect cause) {
        executeList.add(new Event(METHOD_ON_EVENT_DISCONNECT, node, null));
        latch.countDown();
    }

    @Override
    public void onEventStarted(Node node, RuntimeComponentInfo componentInfo) {
        executeList.add(new Event(METHOD_ON_EVENT_STARTED, node, null));
        latch.countDown();
    }

    @Override
    public void onEventAvailable(Node node, RuntimeComponentInfo componentInfo) {
        executeList.add(new Event(METHOD_ON_EVENT_AVAILABLE, node, null));
        latch.countDown();
    }

    @Override
    public void onEventUnavailable(Node node, RuntimeComponentInfo componentInfo) {
        executeList.add(new Event(METHOD_ON_EVENT_UNAVAILABLE, node, null));
        latch.countDown();
    }

    public CopyOnWriteArrayList<Event> getExecuteList() {
        return executeList;
    }
}
