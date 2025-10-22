package ru.itmo.isitmolab.ws;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.websocket.Session;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class VehicleWsService {

    private final Set<Session> sessions = ConcurrentHashMap.newKeySet(); // при ws вызовы с разных потоках

    public void add(Session s) {
        sessions.add(s);
    }

    public void remove(Session s) {
        sessions.remove(s);
    }

    public void broadcastText(String text) {
        for (Session s : sessions) {
            if (s.isOpen()) {
                try {
                    s.getAsyncRemote().sendText(text);
                } catch (IllegalStateException ignored) {

                }
            }
        }
    }

}