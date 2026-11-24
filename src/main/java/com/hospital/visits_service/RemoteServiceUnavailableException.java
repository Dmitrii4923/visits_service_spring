package com.hospital.visits_service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Техническая ошибка при обращении к соседнему микросервису.
 * Не считается ошибкой бизнес-логики и учитывается Circuit Breaker-ом.
 */
@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class RemoteServiceUnavailableException extends RuntimeException {

    public RemoteServiceUnavailableException(String message) {
        super(message);
    }

    public RemoteServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
