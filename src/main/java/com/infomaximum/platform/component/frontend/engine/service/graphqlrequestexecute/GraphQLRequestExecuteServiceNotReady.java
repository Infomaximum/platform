package com.infomaximum.platform.component.frontend.engine.service.graphqlrequestexecute;

import com.infomaximum.cluster.graphql.struct.GRequest;
import com.infomaximum.platform.Platform;
import com.infomaximum.platform.component.frontend.engine.service.graphqlrequestexecute.struct.GraphQLResponse;
import com.infomaximum.platform.component.frontend.engine.service.graphqlrequestexecute.utils.GraphQLExecutionResultUtils;
import com.infomaximum.platform.exception.GraphQLWrapperPlatformException;
import com.infomaximum.platform.exception.PlatformException;
import com.infomaximum.platform.sdk.exception.GeneralExceptionBuilder;
import net.minidev.json.JSONObject;

import java.util.concurrent.CompletableFuture;

/**
 * Стаб {@link GraphQLRequestExecuteService}, отдаваемый клиенту пока система не находится
 * в фазе {@link com.infomaximum.platform.state.SystemState#READY}.
 */
public class GraphQLRequestExecuteServiceNotReady implements GraphQLRequestExecuteService {

    private final Platform platform;

    public GraphQLRequestExecuteServiceNotReady(Platform platform) {
        this.platform = platform;
    }

    @Override
    public CompletableFuture<GraphQLResponse> execute(GRequest gRequest) {
        PlatformException exception = GeneralExceptionBuilder.buildSystemNotReadyException(platform.getSystemState());
        GraphQLWrapperPlatformException wrapped = GraphQLExecutionResultUtils.coercionGraphQLPlatformException(exception);
        return CompletableFuture.completedFuture(buildResponse(wrapped));
    }

    @Override
    public GraphQLResponse<JSONObject> buildResponse(GraphQLWrapperPlatformException graphQLPlatformException) {
        PlatformException e = graphQLPlatformException.getPlatformException();

        JSONObject error = new JSONObject();
        error.put("code", e.getCode());
        error.put("message", e.getComment());

        if (e.getParameters() != null && !e.getParameters().isEmpty()) {
            error.put("parameters", new JSONObject(e.getParameters()));
        }

        return new GraphQLResponse<>(error, true, graphQLPlatformException.getStatistics());
    }
}
