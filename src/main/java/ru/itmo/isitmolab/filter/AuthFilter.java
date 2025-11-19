package ru.itmo.isitmolab.filter;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import ru.itmo.isitmolab.service.SessionService;

import java.util.Map;

@Provider
@Priority(Priorities.AUTHENTICATION)
public class AuthFilter implements ContainerRequestFilter {

    @Context
    HttpServletRequest request;

    @Inject
    SessionService sessionService;

    @Override
    public void filter(ContainerRequestContext ctx) {
        final String path = ctx.getUriInfo().getPath();
        final String method = ctx.getMethod();

        if ("OPTIONS".equalsIgnoreCase(method)) {
            return;
        }

        if (path.contains("auth")) {
            return;
        }

        boolean ok = sessionService.isActive(request.getSession(false));

        if (!ok) {
            ctx.abortWith(
                    Response.status(Response.Status.UNAUTHORIZED)
                            .entity(Map.of("message", "Unauthorized"))
                            .build()
            );
        }
    }

}
