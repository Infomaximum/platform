package com.infomaximum.platform.service;

import com.infomaximum.cluster.Cluster;
import com.infomaximum.cluster.Node;
import com.infomaximum.cluster.core.component.RuntimeComponentInfo;
import com.infomaximum.cluster.core.service.transport.network.local.SingletonNode;
import com.infomaximum.cluster.struct.Version;
import com.infomaximum.platform.Platform;
import com.infomaximum.testcomponent.RemoteNode;
import com.infomaximum.testcomponent.TestEventComponent;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static com.infomaximum.platform.sdk.component.ComponentEvent.*;
import static com.infomaximum.platform.service.ComponentEventQueue.*;
import static org.mockito.Mockito.*;

public class ComponentEventServiceTest {

    private TestEventComponent component;
    private ComponentEventService componentEventService;
    private Platform platform;
    private final static Node localNode = new SingletonNode();
    private final static Node remoteNode = new RemoteNode();

    @BeforeEach
    public void init() {
        this.platform = mock(Platform.class);
        Cluster cluster = mock(Cluster.class);
        this.component = new TestEventComponent(platform);
        this.componentEventService = new ComponentEventService(platform);
        when(platform.getCluster())
                .thenReturn(cluster);
        when(cluster.getDependencyOrderedComponentsOf(any()))
                .thenReturn(List.of(component));
    }

    @ParameterizedTest
    @MethodSource("getArguments")
    public void test(List<Event> expectedEvents,
                     Consumer<ComponentEventService> eventConsumerBeforeOnStarted,
                     Consumer<ComponentEventService> eventConsumerAfterOnStarted) {
        eventConsumerBeforeOnStarted.accept(componentEventService);

        Assertions.assertThat(component.getComponentEventQueue(platform).executeAll()).isFalse();
        Assertions.assertThat(component.getExecuteList()).hasSize(0);
        component.onStarted();

        eventConsumerAfterOnStarted.accept(componentEventService);
        try {
            Thread.sleep(100); //Необходимо время для выполнения событий в других потоках
        } catch (InterruptedException e) {
            Assertions.fail(e);
        }
        Assertions.assertThat(component.getExecuteList())
                .hasSize(expectedEvents.size())
                .containsExactlyInAnyOrderElementsOf(expectedEvents);
    }

    private static Stream<Arguments> getArguments() {
        return Stream.of(
                testCase1(),
                testCase2(),
                testCase3(),
                testCase4()
        );
    }

    private static Arguments testCase1() {
        List<Event> expectedEvents = List.of();
        Consumer<ComponentEventService> consumerBeforeOnStart = eventService -> {};
        Consumer<ComponentEventService> consumerAfterOnStart = eventService -> {};
        return Arguments.of(expectedEvents, consumerBeforeOnStart, consumerAfterOnStart);
    }

    private static Arguments testCase2() {
        List<Event> expectedEvents = List.of(
                new Event(METHOD_ON_EVENT_STARTED, localNode, null),
                new Event(METHOD_ON_EVENT_AVAILABLE, localNode, null)
        );
        Consumer<ComponentEventService> consumerBeforeOnStart = eventService -> {
            eventService.pushEventOnConnect(remoteNode);
            eventService.pushEventOnAvailable(remoteNode, new RuntimeComponentInfo(1, "uuid-2", Version.parse("1.0.0.0"), new HashSet<>()));
            eventService.pushEventOnStarted(localNode, new RuntimeComponentInfo("uuid-1", Version.parse("1.0.0.0"), new HashSet<>()));
            eventService.pushEventOnAvailable(localNode, new RuntimeComponentInfo("uuid-1", Version.parse("1.0.0.0"), new HashSet<>()));
            eventService.pushEventOnDisconnect(remoteNode, null);
        };
        Consumer<ComponentEventService> consumerAfterOnStart = eventService -> {};
        return Arguments.of(expectedEvents, consumerBeforeOnStart, consumerAfterOnStart);
    }

    private static Arguments testCase3() {
        List<Event> expectedEvents = List.of(
                new Event(METHOD_ON_EVENT_CONNECT, remoteNode, null),
                new Event(METHOD_ON_EVENT_STARTED, localNode, null),
                new Event(METHOD_ON_EVENT_AVAILABLE, localNode, null),
                new Event(METHOD_ON_EVENT_DISCONNECT, remoteNode, null),
                new Event(METHOD_ON_EVENT_UNAVAILABLE, localNode, null)
        );
        Consumer<ComponentEventService> consumerBeforeOnStart = eventService -> {
            eventService.pushEventOnConnect(remoteNode);
            eventService.pushEventOnStarted(localNode, new RuntimeComponentInfo("uuid-1", Version.parse("1.0.0.0"), new HashSet<>()));
            eventService.pushEventOnAvailable(localNode, new RuntimeComponentInfo("uuid-1", Version.parse("1.0.0.0"), new HashSet<>()));
        };
        Consumer<ComponentEventService> consumerAfterOnStart = eventService -> {
            eventService.pushEventOnDisconnect(remoteNode, null);
            eventService.pushEventOnStopped(localNode, new RuntimeComponentInfo("uuid-1", Version.parse("1.0.0.0"), new HashSet<>()));
            eventService.pushEventOnUnavailable(localNode, new RuntimeComponentInfo("uuid-1", Version.parse("1.0.0.0"), new HashSet<>()));
        };
        return Arguments.of(expectedEvents, consumerBeforeOnStart, consumerAfterOnStart);
    }

    private static Arguments testCase4() {
        List<Event> expectedEvents = List.of();
        Consumer<ComponentEventService> consumerBeforeOnStart = eventService -> {
            eventService.pushEventOnConnect(remoteNode);
            eventService.pushEventOnDisconnect(remoteNode, null);
            eventService.pushEventOnConnect(remoteNode);
            eventService.pushEventOnDisconnect(remoteNode, null);
            eventService.pushEventOnConnect(remoteNode);
            eventService.pushEventOnDisconnect(remoteNode, null);
        };
        Consumer<ComponentEventService> consumerAfterOnStart = eventService -> {};
        return Arguments.of(expectedEvents, consumerBeforeOnStart, consumerAfterOnStart);
    }
}
