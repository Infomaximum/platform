package com.infomaximum.platform.component.frontend.request;

import com.infomaximum.cluster.core.remote.struct.RemoteObject;
import com.infomaximum.cluster.graphql.struct.GRequest;
import jakarta.servlet.http.Cookie;

import java.io.Serializable;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;

public class GRequestHttp extends GRequest {

    private final HashMap<String, String[]> parameters;
    private final HashMap<String, String[]> attributes;

    private final Cookie[] cookies;

    private final ArrayList<UploadFile> uploadFiles;

    private String idempotencyKey;
    private Integer xRetryCount;

    @Deprecated
    public GRequestHttp(Instant instant, RemoteAddress remoteAddress, String query, HashMap<String, Serializable> queryVariables, String operationName, String xTraceId, HashMap<String, String[]> parameters, HashMap<String, String[]> attributes, Cookie[] cookies, ArrayList<UploadFile> uploadFiles) {
        super(instant, remoteAddress, query, queryVariables, operationName, xTraceId);

        this.parameters = parameters;

        this.attributes = attributes;

        this.cookies = cookies;

        this.uploadFiles = uploadFiles;
    }

    public GRequestHttp(Builder builder) {
        super(builder.instant, builder.remoteAddress, builder.query, builder.queryVariables, builder.operationName, builder.xTraceId);

        this.parameters = builder.parameters;
        this.attributes = builder.attributes;
        this.cookies = builder.cookies;
        this.uploadFiles = builder.uploadFiles;
        this.idempotencyKey = builder.idempotencyKey;
        this.xRetryCount = builder.xRetryCount;
    }

    public String getParameter(String name) {
        String[] values = getParameters(name);
        return (values == null) ? null : values[0];
    }

    public String[] getParameters(String name) {
        return parameters.get(name);
    }

    public String[] getAttributes(String name) {
        if (attributes == null) {
            return null;
        }
        return attributes.get(name);
    }

    public Cookie getCookie(String name) {
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (name.equals(cookie.getName())) return cookie;
            }
        }
        return null;
    }

    public HashMap<String, String[]> getParameters() {
        return parameters;
    }

    public HashMap<String, String[]> getAttributes() {
        return attributes;
    }

    public Cookie[] getCookies() {
        return cookies;
    }

    public ArrayList<UploadFile> getUploadFiles() {
        return uploadFiles;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Integer getXRetryCount() {
        return xRetryCount;
    }

    public static class UploadFile implements RemoteObject {

        public final String fieldname;
        public final String filename;
        public final String contentType;
        public final URI uri;
        public final long size;

        public UploadFile(String fieldname, String filename, String contentType, URI uri, long size) {
            this.fieldname = fieldname;
            this.filename = filename;
            this.contentType = contentType;
            this.uri = uri;
            this.size = size;
        }
    }

    public static class Builder {

        private Instant instant;
        private RemoteAddress remoteAddress;
        private String query;
        private HashMap<String, Serializable> queryVariables;
        private String operationName;
        private String xTraceId;

        private Integer xRetryCount;
        private String idempotencyKey;
        private HashMap<String, String[]> parameters;
        private HashMap<String, String[]> attributes;
        private Cookie[] cookies;
        private ArrayList<UploadFile> uploadFiles;

        public Builder withInstantRequest(Instant instant) {
            this.instant = instant;
            return this;
        }

        public Builder withRemoteAddress(RemoteAddress remoteAddress) {
            this.remoteAddress = remoteAddress;
            return this;
        }

        public Builder withQuery(String query) {
            this.query = query;
            return this;
        }

        public Builder withQueryVariables(HashMap<String, Serializable> queryVariables) {
            this.queryVariables = queryVariables;
            return this;
        }

        public Builder withOperationName(String operationName) {
            this.operationName = operationName;
            return this;
        }

        public Builder withXTraceId(String xTraceId) {
            this.xTraceId = xTraceId;
            return this;
        }

        public Builder withXRetryCount(Integer xRetryCount) {
            this.xRetryCount = xRetryCount;
            return this;
        }

        public GRequestHttp.Builder withIdempotencyKey(String idempotencyKey) {
            this.idempotencyKey = idempotencyKey;
            return this;
        }

        public GRequestHttp.Builder withParameters(HashMap<String, String[]> parameters) {
            this.parameters = parameters;
            return this;
        }

        public Builder withAttributes(HashMap<String, String[]> attributes) {
            this.attributes = attributes;
            return this;
        }

        public Builder withCookies(Cookie[] cookies) {
            this.cookies = cookies;
            return this;
        }

        public Builder withUploadFiles(ArrayList<UploadFile> uploadFiles) {
            this.uploadFiles = uploadFiles;
            return this;
        }

        public GRequestHttp build() {
            return new GRequestHttp(this);
        }
    }
}
