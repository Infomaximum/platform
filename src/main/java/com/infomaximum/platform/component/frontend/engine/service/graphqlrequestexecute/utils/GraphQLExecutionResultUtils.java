package com.infomaximum.platform.component.frontend.engine.service.graphqlrequestexecute.utils;

import com.infomaximum.cluster.graphql.executor.struct.GExecutionResult;
import com.infomaximum.cluster.graphql.executor.struct.GSourceLocation;
import com.infomaximum.cluster.graphql.executor.struct.GSubscriptionPublisher;
import com.infomaximum.platform.component.frontend.engine.service.graphqlrequestexecute.struct.GExecutionStatistics;
import com.infomaximum.platform.component.frontend.engine.service.graphqlrequestexecute.struct.GraphQLResponse;
import com.infomaximum.platform.exception.GraphQLWrapperPlatformException;
import com.infomaximum.platform.exception.PlatformException;
import com.infomaximum.platform.exception.runtime.PlatformRuntimeException;
import com.infomaximum.platform.sdk.exception.GeneralExceptionBuilder;
import com.infomaximum.platform.sdk.graphql.out.GOutputFile;
import com.infomaximum.platform.utils.ExceptionUtils;
import graphql.AssertException;
import graphql.ExceptionWhileDataFetching;
import graphql.GraphQLError;
import graphql.execution.NonNullableValueCoercedAsNullException;
import graphql.language.SourceLocation;
import graphql.schema.CoercingParseValueException;
import net.minidev.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GraphQLExecutionResultUtils {


    //Формируем пути по которым происходили access_denied
    public static String getAccessDenied(GExecutionResult executionResult, Thread.UncaughtExceptionHandler uncaughtExceptionHandler) {
        if (executionResult.getErrors() == null || executionResult.getErrors().isEmpty()) {
            return null;
        }

        Map<String, Integer> errors = new HashMap<>();
        for (GraphQLError graphQLError : executionResult.getErrors()) {
            if (!(graphQLError instanceof ExceptionWhileDataFetching)) {
                continue;
            }
            ExceptionWhileDataFetching exceptionWhileDataFetching = (ExceptionWhileDataFetching) graphQLError;
            Throwable exception = exceptionWhileDataFetching.getException();

            PlatformException platformException;
            if (exception instanceof PlatformException) {
                platformException = (PlatformException) exception;
            } else if (exception instanceof PlatformRuntimeException platformRuntimeException) {
                platformException = platformRuntimeException.getPlatformException();
            } else {
                uncaughtExceptionHandler.uncaughtException(Thread.currentThread(), exception);
                return null;
            }

            if (platformException.getCode().equals(GeneralExceptionBuilder.ACCESS_DENIED_CODE)) {
                String path = "/" + graphQLError.getPath().stream()
                        .filter(o -> (o instanceof String)).map(o -> (String) o)
                        .collect(Collectors.joining("/"));

                errors.compute(path, (s, integer) -> (integer == null) ? 1 : integer + 1);
            }
        }
        if (errors.isEmpty()) {
            return null;
        }

        return errors.entrySet().stream().map(entry -> entry.getKey() + " (" + entry.getValue() + ")").collect(Collectors.joining(", "));
    }

    // Формирование ответа из результата
    public static GraphQLResponse<JSONObject> buildResponse(GExecutionResult executionResult, GExecutionStatistics statistics) {
        GOutputFile gOutputFile = findOutputFile(executionResult.getData());
        if (gOutputFile != null) {
            return new GraphQLResponse(gOutputFile, false, statistics);
        } else {
            if (executionResult.getData() instanceof GSubscriptionPublisher) {
                return new GraphQLResponse(executionResult.getData(), false, statistics);
            } else {
                return new GraphQLResponse(new JSONObject(executionResult.getData()), false, statistics);
            }
        }
    }

    // Оборачиваем ошибку в обертку
    public static GraphQLWrapperPlatformException coercionGraphQLPlatformException(Throwable throwable) {
        if (throwable instanceof GraphQLWrapperPlatformException) {
            return (GraphQLWrapperPlatformException) throwable;
        } else if (throwable instanceof PlatformException) {
            return new GraphQLWrapperPlatformException((PlatformException) throwable);
        } else if (throwable instanceof PlatformRuntimeException) {
            return new GraphQLWrapperPlatformException(((PlatformRuntimeException) throwable).getPlatformException());
        } else if (throwable instanceof AssertException
                || throwable instanceof CoercingParseValueException
                || throwable instanceof NonNullableValueCoercedAsNullException
        ) {
            List<GSourceLocation> locations = null;
            if (throwable instanceof GraphQLError graphQLError) {
                locations = convertLocation(graphQLError);
            }
            return new GraphQLWrapperPlatformException(
                    GeneralExceptionBuilder.buildGraphQLValidationException(throwable.getMessage()),
                    locations,
                    null
            );
        } else {
            throw ExceptionUtils.coercionRuntimeException(throwable);
        }
    }

    public static List<GSourceLocation> convertLocation(GraphQLError graphQLError) {
        List<SourceLocation> nLocations = graphQLError.getLocations();
        if (nLocations != null) {
            return nLocations.stream().map(GSourceLocation::new).toList();
        } else {
            return null;
        }
    }

    private static GOutputFile findOutputFile(Object response) {
        if (response instanceof Map) {
            for (Object oEntry : ((Map) response).entrySet()) {
                Map.Entry<String, Object> entry = (Map.Entry<String, Object>) oEntry;
                GOutputFile iFindOutputFile = findOutputFile(entry.getValue());
                if (iFindOutputFile != null) return iFindOutputFile;
            }
        } else if (response instanceof GOutputFile) {
            return (GOutputFile) response;
        }
        return null;
    }
}
