package ru.itmo.isitmolab.ws;

import jakarta.enterprise.inject.spi.CDI;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;

@ServerEndpoint("/ws/vehicles")
public class VehicleWsController {

    private VehicleWsService hub() {
        return CDI.current().select(VehicleWsService.class).get(); // не CDI-бин
    }

    @OnOpen
    public void onOpen(Session session, EndpointConfig config) {
        hub().add(session);
    }

    @OnClose
    public void onClose(Session session, CloseReason reason) {
        hub().remove(session);
    }

    @OnError
    public void onError(Session session, Throwable thr) {
        hub().remove(session);
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        // входящие
    }

}
