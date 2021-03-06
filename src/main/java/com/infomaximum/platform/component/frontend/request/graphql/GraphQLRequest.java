package com.infomaximum.platform.component.frontend.request.graphql;

import com.infomaximum.cluster.graphql.struct.GRequest;
import com.infomaximum.platform.component.frontend.request.GRequestHttp;
import com.infomaximum.platform.component.frontend.request.graphql.builder.ClearUploadFiles;

public class GraphQLRequest implements AutoCloseable {

    private final GRequestHttp gRequest;
    private final ClearUploadFiles clearUploadFiles;

    public GraphQLRequest(
            GRequestHttp gRequest,
            ClearUploadFiles clearUploadFiles
    ) {
        this.gRequest = gRequest;
        this.clearUploadFiles = clearUploadFiles;
    }

    public GRequest getGRequest() {
        return gRequest;
    }

    @Override
    public void close() {
        clearUploadFiles.clear(gRequest.getUploadFiles());
    }
}
