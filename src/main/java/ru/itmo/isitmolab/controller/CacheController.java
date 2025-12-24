package ru.itmo.isitmolab.controller;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.hibernate.stat.Statistics;
import ru.itmo.isitmolab.util.l2.HibernateStatsService;
import ru.itmo.isitmolab.util.l2.L2CacheStatsToggle;

import java.util.LinkedHashMap;
import java.util.Map;

@Path("/cache")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequestScoped
public class CacheController {

    @Inject
    L2CacheStatsToggle toggle;

    @Inject
    HibernateStatsService statsService;

    @GET
    @Path("/logging")
    public Map<String, Object> getLoggingEnabled() {
        return Map.of("enabled", toggle.isEnabled());
    }

    @POST
    @Path("/logging")
    public Map<String, Object> setLoggingEnabled(@QueryParam("enabled") Boolean enabled) {
        if (enabled == null) {
            throw new BadRequestException("Query param 'enabled' is required");
        }
        toggle.setEnabled(enabled);
        return Map.of("enabled", toggle.isEnabled());
    }

    @GET
    @Path("/stats")
    public Response getStats() {
        Statistics st;
        try {
            st = statsService.stats();
        } catch (Exception e) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(Map.of("message", "Hibernate statistics unavailable", "details", e.getMessage()))
                    .build();
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("loggingEnabled", toggle.isEnabled());
        body.put("statisticsEnabled", st.isStatisticsEnabled());
        body.put("l2CacheHitCount", st.getSecondLevelCacheHitCount());
        body.put("l2CacheMissCount", st.getSecondLevelCacheMissCount());
        body.put("l2CachePutCount", st.getSecondLevelCachePutCount());
        body.put("l2CacheRegionCount", st.getSecondLevelCacheRegionNames() == null ? 0 : st.getSecondLevelCacheRegionNames().length);
        return Response.ok(body).build();
    }

    @POST
    @Path("/stats/reset")
    public Response resetStats() {
        Statistics st;
        try {
            st = statsService.stats();
        } catch (Exception e) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(Map.of("message", "Hibernate statistics unavailable", "details", e.getMessage()))
                    .build();
        }
        st.clear();
        return Response.noContent().build();
    }
}

