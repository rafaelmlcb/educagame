package com.educagame.resource;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.MDC;

import java.io.IOException;
import java.util.UUID;

/**
 * Adds a correlation id to every REST request.
 *
 * - Reads the incoming header {@code X-Request-Id} when present.
 * - Otherwise generates a new UUID.
 * - Exposes the id back to the client as {@code X-Request-Id}.
 * - Stores it in the logging MDC as {@code requestId} so all backend logs can be correlated.
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
public class RequestIdFilter implements ContainerRequestFilter, ContainerResponseFilter {

    public static final String HEADER_REQUEST_ID = "X-Request-Id";
    public static final String MDC_KEY_REQUEST_ID = "requestId";

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String requestId = requestContext.getHeaderString(HEADER_REQUEST_ID);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        requestContext.setProperty(MDC_KEY_REQUEST_ID, requestId);
        MDC.put(MDC_KEY_REQUEST_ID, requestId);
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        Object requestIdObj = requestContext.getProperty(MDC_KEY_REQUEST_ID);
        String requestId = requestIdObj != null ? String.valueOf(requestIdObj) : null;
        if (requestId != null && !requestId.isBlank()) {
            responseContext.getHeaders().putSingle(HEADER_REQUEST_ID, requestId);
        }
        MDC.remove(MDC_KEY_REQUEST_ID);
    }
}
