package com.infomaximum.platform.component.frontend.request.graphql.builder.impl;

import com.infomaximum.cluster.graphql.struct.GRequest;
import com.infomaximum.platform.component.frontend.engine.uploadfile.FrontendMultipartSource;
import com.infomaximum.platform.component.frontend.request.GRequestHttp;
import com.infomaximum.platform.component.frontend.request.graphql.GraphQLRequest;
import com.infomaximum.platform.component.frontend.request.graphql.builder.ClearUploadFiles;
import com.infomaximum.platform.component.frontend.request.graphql.builder.GraphQLRequestBuilder;
import com.infomaximum.platform.component.frontend.request.graphql.builder.impl.attribute.GraphQLRequestAttributeBuilder;
import com.infomaximum.platform.component.frontend.request.graphql.builder.impl.attribute.GraphQLRequestAttributeBuilderEmpty;
import com.infomaximum.platform.sdk.exception.GeneralExceptionBuilder;
import com.infomaximum.subsystems.exception.SubsystemException;
import net.minidev.json.JSONObject;
import net.minidev.json.parser.JSONParser;
import net.minidev.json.parser.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jetty.http.BadMessageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.commons.CommonsMultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Serializable;
import java.time.Instant;
import java.util.*;

public class DefaultGraphQLRequestBuilder implements GraphQLRequestBuilder {

    private final static Logger log = LoggerFactory.getLogger(DefaultGraphQLRequestBuilder.class);

    private static final String QUERY_PARAM = "query";
    private static final String VARIABLES_PARAM = "variables";

    private final FrontendMultipartSource frontendMultipartSource;
    private final ClearUploadFiles clearUploadFiles;

    private final GraphQLRequestAttributeBuilder attributeBuilder;

    public DefaultGraphQLRequestBuilder(
            FrontendMultipartSource frontendMultipartSource,
            GraphQLRequestAttributeBuilder attributeBuilder
    ) {
        this.frontendMultipartSource = frontendMultipartSource;
        this.clearUploadFiles = new ClearUploadFilesImpl(frontendMultipartSource);

        this.attributeBuilder = attributeBuilder;
    }

    @Override
    public GraphQLRequest build(HttpServletRequest request) throws SubsystemException {
        ArrayList<GRequestHttp.UploadFile> uploadFiles = null;//???????? ???????? ?????????????????? ???????????? ???? ?????????????????????? ??????????

        String rawRemoteAddress = request.getRemoteAddr();
        String endRemoteAddress = request.getHeader("X-Real-IP");
        if (endRemoteAddress == null) {
            endRemoteAddress = rawRemoteAddress;
        }
        GRequest.RemoteAddress remoteAddress = new GRequest.RemoteAddress(rawRemoteAddress, endRemoteAddress);

        HashMap<String, String[]> parameters = new HashMap<>();

        try {
            //???????????????? ??????????????????
            String query = request.getParameter(QUERY_PARAM);
            HashMap<String, Serializable> queryVariables = null;

            String variablesJson = request.getParameter(VARIABLES_PARAM);
            if (variablesJson != null) {
                JSONObject variables = parseJSONObject(variablesJson);
                if (variables != null) {
                    queryVariables = new HashMap<>((Map) variables);
                }
            }

            Enumeration<String> parameterNames = request.getParameterNames();
            while (parameterNames.hasMoreElements()) {
                String parameterName = parameterNames.nextElement();
                parameters.put(parameterName, request.getParameterValues(parameterName));
            }

            if (request instanceof MultipartHttpServletRequest) {//?????????????????? ???????????????? ?????? Multipart request
                MultipartHttpServletRequest multipartRequest = (MultipartHttpServletRequest) request;

                Map<String, String[]> multipartParameters = multipartRequest.getParameterMap();
                String[] queryArray = multipartParameters.get(QUERY_PARAM);
                if (queryArray != null && queryArray.length > 0) {
                    query = queryArray[0];
                }

                String[] variablesJsonArray = multipartParameters.get(VARIABLES_PARAM);
                if (variablesJsonArray != null && variablesJsonArray.length > 0) {
                    JSONObject variables = parseJSONObject(variablesJsonArray[0]);
                    if (variables != null) {
                        queryVariables = new HashMap<>((Map) variables);
                    }
                }
                multipartParameters.forEach((key, values) -> parameters.put(key, values));

                MultiValueMap<String, MultipartFile> multipartFiles = multipartRequest.getMultiFileMap();
                if (multipartFiles != null && !multipartFiles.isEmpty()) {
                    uploadFiles = new ArrayList<>();

                    for (Map.Entry<String, List<MultipartFile>> entry : multipartFiles.entrySet()) {
                        for (MultipartFile multipartFile : entry.getValue()) {
                            boolean isInMemory = ((CommonsMultipartFile) multipartFile).getFileItem().isInMemory();
                            uploadFiles.add(new GRequestHttp.UploadFile(
                                    entry.getKey(),
                                    multipartFile.getOriginalFilename(),
                                    multipartFile.getContentType(),
                                    frontendMultipartSource.put(multipartFile),
                                    isInMemory,
                                    multipartFile.getSize()
                            ));
                        }
                    }
                }
            } else {//???????? POST ??????????????????
                BufferedReader reader = null;
                try {
                    reader = request.getReader();
                } catch (IllegalStateException ignore) {
                }
                if (reader != null) {
                    JSONObject dataPostVariables = parseJSONObject(request.getReader());
                    if (dataPostVariables != null) {
                        if (dataPostVariables.containsKey(QUERY_PARAM)) {
                            query = dataPostVariables.getAsString(QUERY_PARAM);
                        }

                        Object variables = dataPostVariables.get(VARIABLES_PARAM);
                        if (variables != null && variables instanceof Map) {
                            queryVariables = new HashMap<>((Map) variables);
                        }

                        dataPostVariables.forEach((key, value) -> {
                            if (value instanceof String) {
                                parameters.put(key, new String[]{ (String) value });
                            }
                        });
                    }
                }
            }

            if (StringUtils.isBlank(query)) {
                throw GeneralExceptionBuilder.buildEmptyValueException(QUERY_PARAM);
            }

            HashMap<String, String[]> attributes = attributeBuilder.build(request);

            GRequestHttp gRequest = new GRequestHttp(
                    Instant.now(),
                    remoteAddress,
                    query, queryVariables != null ? queryVariables : new HashMap<>(),
                    parameters,
                    attributes,
                    request.getCookies(),
                    uploadFiles
            );

            return new GraphQLRequest(
                    gRequest,
                    clearUploadFiles
            );
        } catch (BadMessageException | IOException pe) {
            clearUploadFiles.clear(uploadFiles);
            throw GeneralExceptionBuilder.buildInvalidJsonException(pe);
        } catch (Throwable t) {
            clearUploadFiles.clear(uploadFiles);
            throw t;
        }
    }

    private static JSONObject parseJSONObject(Reader in) throws SubsystemException {
        try {
            Object parseData = new JSONParser(JSONParser.DEFAULT_PERMISSIVE_MODE).parse(in);
            return castToJSONObject(parseData);
        } catch (ParseException e) {
            throw GeneralExceptionBuilder.buildInvalidJsonException(e);
        }
    }

    private static JSONObject parseJSONObject(String in) throws SubsystemException {
        try {
            Object parseData = new JSONParser(JSONParser.DEFAULT_PERMISSIVE_MODE).parse(in);
            return castToJSONObject(parseData);
        } catch (ParseException e) {
            throw GeneralExceptionBuilder.buildInvalidJsonException(e);
        }
    }

    private static JSONObject castToJSONObject(Object obj) throws SubsystemException {
        if (obj instanceof JSONObject) {
            return (JSONObject) obj;
        } else if (obj instanceof String) {
            if (((String) obj).isEmpty()) {
                return null;
            }
        }

        throw GeneralExceptionBuilder.buildInvalidJsonException();
    }


    public static class Builder extends GraphQLRequestBuilder.Builder {

        private GraphQLRequestAttributeBuilder attributeBuilder;

        public Builder() {
            attributeBuilder = new GraphQLRequestAttributeBuilderEmpty();
        }

        public Builder withAttributeBuilder(GraphQLRequestAttributeBuilder attributeBuilder) {
            this.attributeBuilder = attributeBuilder;
            return this;
        }

        public GraphQLRequestBuilder build(
                FrontendMultipartSource frontendMultipartSource
        ) {
            return new DefaultGraphQLRequestBuilder(frontendMultipartSource, attributeBuilder);
        }

    }
}
