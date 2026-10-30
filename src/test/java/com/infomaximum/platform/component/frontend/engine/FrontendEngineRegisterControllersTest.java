package com.infomaximum.platform.component.frontend.engine;

import com.infomaximum.cluster.core.remote.AbstractRController;
import com.infomaximum.cluster.core.remote.controller.clusterfile.RControllerClusterFileImpl;
import com.infomaximum.cluster.core.service.transport.executor.ComponentExecutorTransportImpl;
import com.infomaximum.cluster.graphql.GraphQLEngine;
import com.infomaximum.cluster.graphql.remote.graphql.subscribe.RControllerGraphQLSubscribeImpl;
import com.infomaximum.network.builder.BuilderNetwork;
import com.infomaximum.platform.Platform;
import com.infomaximum.platform.component.frontend.request.graphql.builder.GraphQLRequestBuilder;
import com.infomaximum.platform.sdk.component.Component;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;

import java.util.List;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Регистрация удалённых контроллеров frontend-движка: контроллер GraphQL-подписок
 * регистрируется по умолчанию и не регистрируется при отключённых подписках или GraphQL.
 */
class FrontendEngineRegisterControllersTest {

    @Test
    void subscriptionControllerRegisteredByDefault() {
        assertThat(registeredControllers(builder -> builder))
                .anyMatch(RControllerGraphQLSubscribeImpl.class::isInstance);
    }

    @Test
    void subscriptionControllerNotRegisteredWhenSubscriptionDisabled() {
        assertThat(registeredControllers(builder -> builder.withGraphQLSubscriptionDisabled(true)))
                .noneMatch(RControllerGraphQLSubscribeImpl.class::isInstance)
                .anyMatch(RControllerClusterFileImpl.class::isInstance);
    }

    @Test
    void subscriptionControllerNotRegisteredWhenGraphQLDisabled() {
        assertThat(registeredControllers(builder -> builder.withGraphQLDisabled(true)))
                .noneMatch(RControllerGraphQLSubscribeImpl.class::isInstance);
    }

    private static List<AbstractRController> registeredControllers(UnaryOperator<FrontendEngine.Builder> configure) {
        Component component = mock(Component.class);
        GraphQLEngine graphQLEngine = mock(GraphQLEngine.class);
        when(graphQLEngine.buildRemoteControllerGraphQLSubscribe(any(), any()))
                .thenReturn(mock(RControllerGraphQLSubscribeImpl.class));
        Platform platform = mock(Platform.class);
        when(platform.getGraphQLEngine()).thenReturn(graphQLEngine);
        BuilderNetwork builderNetwork = mock(BuilderNetwork.class);
        when(builderNetwork.getBuilderTransports()).thenReturn(List.of());

        FrontendEngine engine = configure.apply(new FrontendEngine.Builder(platform, component)
                        .withBuilderNetwork(builderNetwork)
                        .withGraphQLRequestBuilder(mock(GraphQLRequestBuilder.Builder.class)))
                .build();

        ComponentExecutorTransportImpl.Builder transportBuilder = mock(ComponentExecutorTransportImpl.Builder.class);
        when(transportBuilder.withRemoteController(any())).thenReturn(transportBuilder);
        try (MockedConstruction<RControllerClusterFileImpl> ignored = mockConstruction(RControllerClusterFileImpl.class)) {
            engine.registerControllers(transportBuilder);
        }

        ArgumentCaptor<AbstractRController> captor = ArgumentCaptor.forClass(AbstractRController.class);
        verify(transportBuilder, atLeastOnce()).withRemoteController(captor.capture());
        return captor.getAllValues();
    }
}
