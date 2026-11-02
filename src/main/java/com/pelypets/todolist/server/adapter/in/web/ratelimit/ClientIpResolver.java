package com.pelypets.todolist.server.adapter.in.web.ratelimit;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Behind a trusted reverse proxy the client IP is the last X-Forwarded-For entry, the one the proxy appended itself;
 * earlier entries are sent by the client and can be forged.
 */
public class ClientIpResolver {

    static final String FORWARDED_FOR = "X-Forwarded-For";

    private final boolean trustProxy;

    public ClientIpResolver(boolean trustProxy) {
        this.trustProxy = trustProxy;
    }

    public String resolve(HttpServletRequest request) {
        if (trustProxy) {
            String forwarded = request.getHeader(FORWARDED_FOR);
            if (forwarded != null && !forwarded.isBlank()) {
                String[] hops = forwarded.split(",");
                String last = hops[hops.length - 1].strip();
                if (!last.isEmpty()) return last;
            }
        }
        return request.getRemoteAddr();
    }
}
