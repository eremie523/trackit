package com.trackit.trackit.application.ports.services;

public interface ITransactionManager {
    void execute(TransactionAction action);
}
