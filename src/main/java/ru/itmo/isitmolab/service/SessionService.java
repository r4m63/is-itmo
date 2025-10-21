package ru.itmo.isitmolab.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.servlet.http.HttpSession;

@ApplicationScoped
public class SessionService {

    public static final String ATTR_USER_ID = "userId";

    public void startSession(HttpSession session, Long userId) {
        session.setAttribute(ATTR_USER_ID, userId);
    }

    public boolean isActive(HttpSession session) {
        if (session == null) return false;
        Object uid = session.getAttribute(ATTR_USER_ID);
        return (uid instanceof Number);
    }

    public Long getCurrentUserId(HttpSession session) {
        if (session == null) return null;
        Object uid = session.getAttribute(ATTR_USER_ID);
        return (uid instanceof Number) ? ((Number) uid).longValue() : null;
    }

    public void destroySession(HttpSession session) {
        if (session != null) {
            session.invalidate();
        }
    }

}
