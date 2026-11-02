package com.pelypets.todolist.server.adapter.in.web.ratelimit;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Behind trusted reverse proxies each one appends the address it received the request from to X-Forwarded-For, so
 * with {@code trustedHops} proxies the client is that many entries from the end; earlier entries are sent by the
 * client and can be forged.
 */
public class ClientIpResolver {

    static final String FORWARDED_FOR = "X-Forwarded-For";

    private final boolean trustProxy;
    private final int trustedHops;

    public ClientIpResolver(boolean trustProxy, int trustedHops) {
        this.trustProxy = trustProxy;
        this.trustedHops = trustedHops;
    }

    public String resolve(HttpServletRequest request) {
        if (trustProxy) {
            String forwarded = request.getHeader(FORWARDED_FOR);
            if (forwarded != null && !forwarded.isBlank()) {
                String[] hops = forwarded.split(",");
                String client = hops[Math.max(0, hops.length - trustedHops)].strip();
                if (!client.isEmpty()) return client;
            }
        }
        return request.getRemoteAddr();
    }
}
