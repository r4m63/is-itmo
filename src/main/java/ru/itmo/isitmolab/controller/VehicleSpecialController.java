package ru.itmo.isitmolab.controller;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import ru.itmo.isitmolab.dto.VehicleDto;
import ru.itmo.isitmolab.service.VehicleSpecialService;

import java.util.List;
import java.util.Map;

@Path("/vehicle/special")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequestScoped
public class VehicleSpecialController {

    @Inject
    VehicleSpecialService service;

    @GET
    @Path("/min-distance")
    public Response minDistance() {
        return service.findAnyWithMinDistance()
                .map(dto -> Response.ok(dto).build())
                .orElseGet(() -> Response.status(Response.Status.NO_CONTENT).build());
    }

    @GET
    @Path("/count-fuel-gt")
    public Map<String, Long> countFuelGt(@QueryParam("v") float v) {
        return Map.of("count", service.countFuelConsumptionGreaterThan(v));
    }

    @GET
    @Path("/list-fuel-gt")
    public List<VehicleDto> listFuelGt(@QueryParam("v") float v) {
        return service.listFuelConsumptionGreaterThan(v);
    }

    @GET
    @Path("/by-type")
    public List<VehicleDto> byType(@QueryParam("type") String type) {
        if (type == null || type.isBlank()) throw new BadRequestException("type is required");
        return service.listByType(type);
    }

    @GET
    @Path("/by-engine-range")
    public List<VehicleDto> byEngineRange(@QueryParam("min") @Min(0) int min,
                                          @QueryParam("max") @Min(0) int max) {
        if (min > max) throw new BadRequestException("min must be <= max");
        return service.listByEnginePowerBetween(min, max);
    }
}
