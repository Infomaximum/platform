package com.infomaximum.platform.component.frontend.engine.service.graphqlrequestexecute;

import com.infomaximum.cluster.graphql.struct.GRequest;
import com.infomaximum.platform.component.frontend.engine.service.graphqlrequestexecute.struct.GraphQLResponse;
import com.infomaximum.platform.component.frontend.engine.service.graphqlrequestexecute.utils.GraphQLExecutionResultUtils;
import com.infomaximum.platform.exception.GraphQLWrapperPlatformException;
import com.infomaximum.platform.exception.PlatformException;
import com.infomaximum.platform.sdk.exception.GeneralExceptionBuilder;
import net.minidev.json.JSONObject;

import java.util.concurrent.CompletableFuture;

public class GraphQLRequestExecuteServiceDisable implements GraphQLRequestExecuteService {

    @Override
    public CompletableFuture<GraphQLResponse> execute(GRequest gRequest) {
        GraphQLWrapperPlatformException graphQLPlatformException = GraphQLExecutionResultUtils.coercionGraphQLPlatformException(
                GeneralExceptionBuilder.buildIllegalStateException("GraphQL is disable!")
        );
        GraphQLResponse<JSONObject> response = buildResponse(graphQLPlatformException);
        return CompletableFuture.completedFuture(response);
    }

    @Override
    public GraphQLResponse<JSONObject> buildResponse(GraphQLWrapperPlatformException graphQLPlatformException) {
        PlatformException e = graphQLPlatformException.getPlatformException();

        JSONObject error = new JSONObject();
        error.put("code", e.getCode());
        error.put("message", e.getComment());

        return new GraphQLResponse<>(error, true, graphQLPlatformException.getStatistics());
    }
}
