package ru.itmo.isitmolab.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import ru.itmo.isitmolab.dao.AdminDao;
import ru.itmo.isitmolab.dto.CredsDto;

import java.util.Map;


@ApplicationScoped
public class AuthService {

    @Inject
    AdminDao adminDao;

    @Inject
    SessionService sessionService;


    public void login(CredsDto creds, HttpServletRequest req) {
        var admin = adminDao.findByLoginAndPassHash(creds.getLogin(), creds.getPassword())
                .orElseThrow(() -> new WebApplicationException(
                        Response.status(Response.Status.UNAUTHORIZED)
                                .entity(Map.of("message", "Invalid credentials"))
                                .build()
                ));
        sessionService.startSession(req, admin.getId());
    }


    public boolean isSessionActive(HttpServletRequest req) {
        return sessionService.isActive(req);
    }


    public void logout(HttpServletRequest req, HttpServletResponse res) {
        sessionService.destroySession(req, res);
    }


    public Long currentUserId(HttpServletRequest req) {
        return sessionService.getCurrentUserId(req);
    }
}
