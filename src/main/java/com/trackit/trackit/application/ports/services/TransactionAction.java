package com.trackit.trackit.application.ports.services;

import java.sql.Connection;

@FunctionalInterface
public interface TransactionAction {
    void run(Connection conn) throws Exception;
}
