package com.trackit.trackit.application.usecase;

import java.util.Optional;

public interface IUseCase<T, E> {
    Optional<E> execute(T input);
}
