package ru.itmo.isitmolab.controller;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import ru.itmo.isitmolab.dto.GridTableRequest;
import ru.itmo.isitmolab.dto.VehicleDto;
import ru.itmo.isitmolab.dto.VehicleImportItemDto;
import ru.itmo.isitmolab.service.VehicleImportService;
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
    @Inject
    VehicleImportService vehicleImportService;

    @POST
    public Response createVehicle(@Valid VehicleDto dto) {
        Long id = vehicleService.createNewVehicle(dto);
        return Response.status(Response.Status.CREATED)
                .entity(Map.of("id", id))
                .build();
    }

    @PUT
    @Path("/{id:\\d+}")
    public Response updateVehicle(@PathParam("id") Long id, @Valid VehicleDto dto) {
        vehicleService.updateVehicle(id, dto);
        return Response.noContent().build();
    }

    @GET
    @Path("/{id:\\d+}")
    public Response getVehicle(@PathParam("id") Long id) {
        var res = vehicleService.getVehicleById(id);
        return Response.ok(res).build();
    }

    @DELETE
    @Path("/{id:\\d+}")
    public Response deleteVehicle(@PathParam("id") Long id) {
        vehicleService.deleteVehicleById(id);
        return Response.status(Response.Status.NO_CONTENT).build();
    }

    @POST
    @Path("/query")
    public Response queryVehicles(@Valid GridTableRequest req) {
        var result = vehicleService.queryVehiclesTable(req);
        return Response.ok(result).build();
    }

    @POST
    @Path("/import")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response importVehicles(List<VehicleImportItemDto> items) {
        vehicleImportService.importVehicles(items);
        return Response.ok().build();
    }

    @GET
    @Path("/import/history")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getImportHistory(@QueryParam("limit") @DefaultValue("50") int limit) {
        var history = vehicleImportService.getHistoryForAdmin(limit);
        return Response.ok(history).build();
    }

}


