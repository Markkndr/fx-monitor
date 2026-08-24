package com.currencyexchange.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidHedgeException extends RuntimeException {
    public InvalidHedgeException(String message) {
        super(message);
    }
}
