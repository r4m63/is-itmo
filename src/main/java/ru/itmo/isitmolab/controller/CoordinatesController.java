package ru.itmo.isitmolab.controller;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import ru.itmo.isitmolab.dto.CoordinatesDto;
import ru.itmo.isitmolab.dto.GridTableRequest;
import ru.itmo.isitmolab.dto.GridTableResponse;
import ru.itmo.isitmolab.service.CoordinatesService;
import ru.itmo.isitmolab.util.BeanValidation;

import java.util.List;
import java.util.Map;

@Path("/coordinates")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequestScoped
public class CoordinatesController {

    @Inject
    CoordinatesService service;

    @POST
    @Path("/query")
    public Response query(GridTableRequest req) {
        BeanValidation.validateOrThrow(req);
        GridTableResponse<CoordinatesDto> result = service.query(req);
        return Response.ok(result).build();
    }

    @GET
    @Path("/{id}")
    public CoordinatesDto getOne(@PathParam("id") Long id) {
        return service.getOne(id);
    }

    @POST
    public Response create(CoordinatesDto dto) {
        BeanValidation.validateOrThrow(dto);
        Long id = service.create(dto);
        return Response.status(Response.Status.CREATED).entity(Map.of("id", id)).build();
    }

    @PUT
    @Path("/{id}")
    public void update(@PathParam("id") Long id, CoordinatesDto dto) {
        BeanValidation.validateOrThrow(dto);
        service.update(id, dto);
    }

    @DELETE
    @Path("/{id}")
    public void delete(@PathParam("id") Long id, @QueryParam("reassignTo") Long reassignTo) {
        service.deleteCoordinates(id, reassignTo);
    }

    @GET
    @Path("/search")
    public List<CoordinatesDto> search(@QueryParam("q") String q, @QueryParam("limit") @DefaultValue("20") int limit) {
        return service.searchShort(q, limit);
    }

}
