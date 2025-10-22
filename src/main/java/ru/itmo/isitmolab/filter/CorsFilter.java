package ru.itmo.isitmolab.filter;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.*;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;
import java.util.Set;

@Provider
@PreMatching // фильтр до контроллера
@Priority(Priorities.AUTHENTICATION - 1)
public class CorsFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final Set<String> ALLOWED_ORIGINS = Set.of(
            "http://127.0.0.1:5173",
            "http://localhost:5173",
            "http://127.0.0.1:22821",
            "http://localhost:22821"
    );

    private static final String ALLOWED_METHODS = "GET,POST,PUT,DELETE,OPTIONS";
    private static final String DEFAULT_ALLOWED_HEADERS = "Content-Type, X-Requested-With, Authorization";

    private boolean isAllowedOrigin(String origin) {
        return origin != null && ALLOWED_ORIGINS.contains(origin);
    }

    @Override
    public void filter(ContainerRequestContext requestContext) {
        final String origin = requestContext.getHeaderString("Origin");

        if ("OPTIONS".equalsIgnoreCase(requestContext.getMethod()) && isAllowedOrigin(origin)) {
            String reqHeaders = requestContext.getHeaderString("Access-Control-Request-Headers");
            if (reqHeaders == null || reqHeaders.isBlank()) reqHeaders = DEFAULT_ALLOWED_HEADERS;

            Response preflight = Response.ok()
                    .header("Access-Control-Allow-Origin", origin)
                    .header("Vary", "Origin")
                    .header("Access-Control-Allow-Credentials", "true")
                    .header("Access-Control-Allow-Methods", ALLOWED_METHODS)
                    .header("Access-Control-Allow-Headers", reqHeaders)
                    .header("Access-Control-Max-Age", "86400")
                    .build();

            requestContext.abortWith(preflight);
        }
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        final String origin = requestContext.getHeaderString("Origin");
        if (!isAllowedOrigin(origin)) return;

        responseContext.getHeaders().putSingle("Access-Control-Allow-Origin", origin);
        responseContext.getHeaders().putSingle("Vary", "Origin");
        responseContext.getHeaders().putSingle("Access-Control-Allow-Credentials", "true");
        responseContext.getHeaders().putSingle("Access-Control-Allow-Methods", ALLOWED_METHODS);
        responseContext.getHeaders().putSingle("Access-Control-Expose-Headers", "Location");

        String reqHeaders = requestContext.getHeaderString("Access-Control-Request-Headers");
        if (reqHeaders == null || reqHeaders.isBlank()) reqHeaders = DEFAULT_ALLOWED_HEADERS;
        responseContext.getHeaders().putSingle("Access-Control-Allow-Headers", reqHeaders);
    }

}
