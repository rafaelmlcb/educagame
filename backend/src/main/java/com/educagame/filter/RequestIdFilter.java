package com.educagame.filter;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.MDC;

import java.io.IOException;

@Provider
@Priority(Priorities.USER)
public class RequestIdFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final String HEADER = "X-Request-Id";

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String requestId = requestContext.getHeaderString(HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = java.util.UUID.randomUUID().toString();
        }
        MDC.put("requestId", requestId);
        // ensure downstream sees the header
        requestContext.getHeaders().putSingle(HEADER, requestId);
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        String requestId = MDC.get("requestId");
        if (requestId != null && !requestId.isBlank()) {
            responseContext.getHeaders().putSingle(HEADER, requestId);
        }
        MDC.remove("requestId");
    }
}
