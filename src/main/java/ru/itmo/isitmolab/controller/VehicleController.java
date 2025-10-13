package ru.itmo.isitmolab.controller;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import ru.itmo.isitmolab.dto.GridTableRequest;
import ru.itmo.isitmolab.dto.VehicleDto;
import ru.itmo.isitmolab.service.VehicleService;

import java.util.List;
import java.util.Map;

@Path("/vehicle")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequestScoped
public class VehicleController {

    @Inject
    VehicleService vehicleService;

    @Context
    HttpServletRequest request;

    @POST
    public Response createVehicle(@Valid VehicleDto dto) {
        Long id = vehicleService.createNewVehicle(dto, request);
        return Response.status(Response.Status.CREATED)
                .entity(Map.of("id", id))
                .build();
    }

    @PUT
    @Path("/{id}")
    public Response updateVehicle(@PathParam("id") Long id, @Valid VehicleDto dto) {
        vehicleService.updateVehicle(id, dto);
        return Response.noContent().build();
    }

    @GET
    @Path("/{id}")
    public Response getVehicle(@PathParam("id") Long id) {
        var res = vehicleService.getVehicleById(id);
        return Response.ok(res).build();
    }

    @DELETE
    @Path("/{id}")
    public Response deleteVehicle(@PathParam("id") Long id) {
        vehicleService.deleteVehicleById(id);
        return Response.status(Response.Status.NO_CONTENT).build();
    }

    @GET
    public Response listAllVehicles(
            @QueryParam("offset") @DefaultValue("0") int offset,
            @QueryParam("limit") @DefaultValue("1000") int limit
    ) {
        List<VehicleDto> res = vehicleService.getAllVehicles();
        return Response.ok(res).build();
    }

    @POST
    @Path("/query")
    public Response queryVehicles(@Valid GridTableRequest req) {
        var result = vehicleService.queryTableGridFilters(req);
        return Response.ok(result).build();
    }

    @GET
    @Path("/at-coordinates/{coordinatesId}")
    public List<Map<String, Object>> listByCoordinates(@PathParam("coordinatesId") Long coordinatesId) {
        return vehicleService.findByCoordinates(coordinatesId).stream()
                .map(v -> Map.<String, Object>of(
                        "id", v.getId(),
                        "name", v.getName()
                ))
                .toList();
    }

    @POST
    @Path("/reassign-coordinates-bulk")
    public Map<String, Object> reassignCoordinatesBulk(Map<String, Object> body) {
        Long fromCoordId = body.get("fromCoordinatesId") == null ? null : Long.valueOf(body.get("fromCoordinatesId").toString());
        Long toCoordId   = body.get("toCoordinatesId")   == null ? null : Long.valueOf(body.get("toCoordinatesId").toString());
        int updated = vehicleService.reassignCoordinatesBulk(fromCoordId, toCoordId);
        return Map.of("updated", updated);
    }
}
