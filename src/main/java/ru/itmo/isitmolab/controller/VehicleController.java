package ru.itmo.isitmolab.controller;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbException;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import ru.itmo.isitmolab.dto.GridTableRequest;
import ru.itmo.isitmolab.dto.VehicleDto;
import ru.itmo.isitmolab.dto.VehicleImportItemDto;
import ru.itmo.isitmolab.service.VehicleImportService;
import ru.itmo.isitmolab.service.VehicleService;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static java.nio.charset.StandardCharsets.UTF_8;

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
    @Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_OCTET_STREAM}) // двоичные данные без указания конкретного формата
    public Response importVehicles(InputStream importStream) {
        try {
            List<VehicleImportItemDto> items = parseImportFile(importStream);
            vehicleImportService.importVehicles(items);
            return Response.ok().build();
        } catch (BadRequestException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("message", e.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("/import/history")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getImportHistory(@QueryParam("limit") @DefaultValue("50") int limit) {
        var history = vehicleImportService.getHistoryForAdmin(limit);
        return Response.ok(history).build();
    }

    private List<VehicleImportItemDto> parseImportFile(InputStream importStream) {
        if (importStream == null) {
            throw new BadRequestException("Не передан файл для импорта");
        }

        try (Jsonb jsonb = JsonbBuilder.create()) {
            String payload = new String(importStream.readAllBytes(), UTF_8);
            if (payload.isBlank()) {
                throw new BadRequestException("Файл пустой");
            }

            VehicleImportItemDto[] parsed = jsonb.fromJson(payload, VehicleImportItemDto[].class);
            if (parsed == null || parsed.length == 0) {
                throw new BadRequestException("Файл не содержит данных для импорта");
            }

            boolean hasNullItems = Arrays.stream(parsed).anyMatch(Objects::isNull);
            if (hasNullItems) {
                throw new BadRequestException("Файл содержит пустые записи");
            }

            return Arrays.asList(parsed);
        } catch (JsonbException e) {
            throw new BadRequestException("Файл не является корректным JSON-массивом", e);
        } catch (Exception e) {
            throw new BadRequestException("Не удалось прочитать файл", e);
        }
    }

}
