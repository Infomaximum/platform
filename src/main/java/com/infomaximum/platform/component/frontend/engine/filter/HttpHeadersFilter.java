package com.infomaximum.platform.component.frontend.engine.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public class HttpHeadersFilter implements Filter {

    private final String contentSecurityPolicy;
    private final String contentTypeOptions;
    private final String xssProtection;

    public HttpHeadersFilter(Builder builder) {
        this.contentSecurityPolicy = builder.contentSecurityPolicyValue;
        this.contentTypeOptions = builder.contentTypeOptionsValue;
        this.xssProtection = builder.xssProtectionValue;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        if (contentSecurityPolicy != null) {
            httpResponse.setHeader("Content-Security-Policy", contentSecurityPolicy);
        }
        if (contentTypeOptions != null) {
            httpResponse.setHeader("X-Content-Type-Options", contentTypeOptions);
        }
        if (xssProtection != null) {
            httpResponse.setHeader("X-XSS-Protection", xssProtection);
        }
        chain.doFilter(request, response);
    }

    public static class Builder {

        private String contentSecurityPolicyValue = "default-src 'self'; style-src 'self' 'unsafe-inline'; script-src 'self' 'unsafe-inline' 'unsafe-eval'; img-src 'self' data:";
        private String contentTypeOptionsValue = "nosniff";
        private String xssProtectionValue = "1; mode=block";


        public Builder withHeaderContentSecurityPolicy(String contentSecurityPolicy) {
            this.contentSecurityPolicyValue = contentSecurityPolicy;
            return this;
        }

        public Builder withHeaderContentTypeOptions(String contentTypeOptions) {
            this.contentTypeOptionsValue = contentTypeOptions;
            return this;
        }

        public Builder withHeaderXssProtection(String xssProtection) {
            this.xssProtectionValue = xssProtection;
            return this;
        }
    }
}
