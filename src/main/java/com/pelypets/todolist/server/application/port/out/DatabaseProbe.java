package com.pelypets.todolist.server.application.port.out;

public interface DatabaseProbe {

    /** True when a connection can be taken from the pool and answers a trivial query. */
    boolean isReachable();
}
