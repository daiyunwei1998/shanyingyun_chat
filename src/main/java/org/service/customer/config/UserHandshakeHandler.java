package org.service.customer.config;

import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.http.server.ServerHttpRequest;
import java.security.Principal;
import java.util.Map;

public class UserHandshakeHandler extends DefaultHandshakeHandler {
    @Override
    protected Principal determineUser(ServerHttpRequest request,
                                      WebSocketHandler wsHandler,
                                      Map<String, Object> attributes) {
        // Extract username from query parameters
        String query = request.getURI().getQuery();
        String username = null;
        if (query != null) {
            for (String param : query.split("&")) {
                if (param.startsWith("username=")) {
                    username = param.substring("username=".length());
                }
            }
        }

        if (username == null) {
            username = "anonymous";
        }

        String finalUsername = username;
        return new Principal() {
            @Override
            public String getName() {
                return finalUsername;
            }
        };
    }
}
