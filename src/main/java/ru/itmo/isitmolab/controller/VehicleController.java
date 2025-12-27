package ru.itmo.isitmolab.controller;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbException;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import ru.itmo.isitmolab.dto.GridTableRequest;
import ru.itmo.isitmolab.dto.VehicleDto;
import ru.itmo.isitmolab.dto.VehicleImportItemDto;
import ru.itmo.isitmolab.model.VehicleImportOperation;
import ru.itmo.isitmolab.service.VehicleImportService;
import ru.itmo.isitmolab.service.VehicleService;
import ru.itmo.isitmolab.storage.MinioStorageService;
import ru.itmo.isitmolab.util.BeanValidation;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
    @Inject
    MinioStorageService minioStorage;

    @POST
    public Response createVehicle(VehicleDto dto) {
        BeanValidation.validateOrThrow(dto);
        Long id = vehicleService.createNewVehicle(dto);
        return Response.status(Response.Status.CREATED)
                .entity(Map.of("id", id))
                .build();
    }

    @PUT
    @Path("/{id:\\d+}")
    public Response updateVehicle(@PathParam("id") Long id, VehicleDto dto) {
        BeanValidation.validateOrThrow(dto);
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
    public Response queryVehicles(GridTableRequest req) {
        BeanValidation.validateOrThrow(req);
        var result = vehicleService.queryVehiclesTable(req);
        return Response.ok(result).build();
    }

    @POST
    @Path("/import")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_OCTET_STREAM})
    public Response importVehicles(InputStream importStream,
                                   @HeaderParam("Content-Type") String contentType,
                                   @HeaderParam("X-Filename") String fileName) {
        try {
            // input file
            if (importStream == null) throw new BadRequestException("Не передан файл для импорта");
            byte[] bytes;
            try {
                bytes = importStream.readAllBytes();
            } catch (IOException e) {
                throw new BadRequestException("Не удалось прочитать файл", e);
            }
            if (bytes == null || bytes.length == 0) throw new BadRequestException("Файл пустой");

            // parse file
            List<VehicleImportItemDto> items;
            try (Jsonb jsonb = JsonbBuilder.create()) {
                String payload = new String(bytes, UTF_8);
                if (payload.isBlank()) throw new BadRequestException("Файл пустой");
                VehicleImportItemDto[] parsed = jsonb.fromJson(payload, VehicleImportItemDto[].class);
                if (parsed == null || parsed.length == 0)
                    throw new BadRequestException("Файл не содержит данных для импорта");
                if (Arrays.stream(parsed).anyMatch(Objects::isNull))
                    throw new BadRequestException("Файл содержит пустые записи");
                items = Arrays.asList(parsed);
            } catch (JsonbException e) {
                throw new BadRequestException("Файл не является корректным JSON-массивом", e);
            } catch (BadRequestException e) {
                throw e;
            } catch (Exception e) {
                throw new BadRequestException("Не удалось прочитать файл", e);
            }

            vehicleImportService.importVehicles(items, bytes, fileName, contentType);
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

    @GET
    @Path("/import/history/{id}/file")
    public Response downloadImportFile(@PathParam("id") Long id) {
        VehicleImportOperation op = vehicleImportService.getOperationOrThrow(id);
        if (op.getFileObjectKey() == null || op.getFileObjectKey().isBlank())
            throw new NotFoundException("Файл для операции не найден: " + id);

        String ct = (op.getFileContentType() == null || op.getFileContentType().isBlank())
                ? MediaType.APPLICATION_OCTET_STREAM
                : op.getFileContentType();

        try {
            minioStorage.statObject(op.getFileObjectKey());
        } catch (RuntimeException e) {
            throw new NotFoundException("Файл не найден в MinIO для операции " + id + " (key=" + op.getFileObjectKey() + ")");
        }

        StreamingOutput stream = (OutputStream out) -> {
            try (InputStream in = minioStorage.getObject(op.getFileObjectKey())) {
                in.transferTo(out);
            }
        };

        String name = (op.getFileName() == null || op.getFileName().isBlank())
                ? ("import-" + id + ".json")
                : op.getFileName();

        return Response.ok(stream, ct)
                .header("Content-Disposition", "attachment; filename=\"" + name.replace("\"", "") + "\"")
                .build();
    }

}
