package ru.itmo.isitmolab.controller;

import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import ru.itmo.isitmolab.dto.GridTableRequest;
import ru.itmo.isitmolab.dto.GridTableResponse;
import ru.itmo.isitmolab.dto.PersonDto;
import ru.itmo.isitmolab.service.PersonService;

import java.util.List;
import java.util.Map;

@Path("/person")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class PersonController {

    @Inject
    PersonService service;

    @Context
    HttpServletRequest request;

    @POST
    @Path("/query")
    public Response query(@Valid GridTableRequest req) {
        GridTableResponse<PersonDto> result = service.query(req);
        return Response.ok(result).build();
    }

    @GET
    @Path("/{id}")
    public PersonDto getOne(@PathParam("id") Long id) {
        return service.getOne(id);
    }

    @POST
    public Response create(@Valid PersonDto dto) {
        Long id = service.create(dto, request);
        return Response.status(Response.Status.CREATED).entity(Map.of("id", id)).build();
    }

    @PUT
    @Path("/{id}")
    public void update(@PathParam("id") Long id, @Valid PersonDto dto) {
        service.update(id, dto, request);
    }

    @DELETE
    @Path("/{id}")
    public void delete(@PathParam("id") Long id,
                       @QueryParam("reassignTo") Long reassignTo) {
        service.deletePerson(id, reassignTo);
    }

    @GET
    @Path("/search")
    public List<PersonDto> search(
            @QueryParam("q") String q,
            @QueryParam("limit") @DefaultValue("20") int limit
    ) {
        return service.searchShort(q, limit);
    }


    @GET
    @Path("/list")
    public List<PersonDto> list(@QueryParam("limit") @DefaultValue("50") int limit) {
        return service.searchShort(null, limit);
    }

    @GET @Path("/{id}/usage")
    public Map<String, Long> usage(@PathParam("id") Long id) {
        return Map.of("vehicles", service.countVehiclesOf(id));
    }


}
