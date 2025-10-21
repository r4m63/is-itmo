package ru.itmo.isitmolab.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpSession;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import ru.itmo.isitmolab.dao.AdminDao;
import ru.itmo.isitmolab.dto.CredsDto;
import ru.itmo.isitmolab.model.Admin;

import java.util.Map;

@ApplicationScoped
public class AuthService {

    @Inject
    AdminDao adminDao;

    @Inject
    SessionService sessionService;

    public void login(CredsDto creds, HttpSession session) {
        Admin admin = adminDao.findByLoginAndPassHash(creds.getLogin(), creds.getPassword())
                .orElseThrow(() -> new WebApplicationException(
                        Response.status(Response.Status.UNAUTHORIZED)
                                .entity(Map.of("message", "Invalid credentials"))
                                .build()
                ));
        sessionService.startSession(session, admin.getId());
    }

    public boolean isSessionActive(HttpSession session) {
        return sessionService.isActive(session);
    }

    public void logout(HttpSession session) {
        sessionService.destroySession(session);
    }

}
