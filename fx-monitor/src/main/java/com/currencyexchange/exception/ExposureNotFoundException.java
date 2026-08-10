package com.currencyexchange.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class ExposureNotFoundException extends RuntimeException {
    public ExposureNotFoundException(String message) {
        super(message);
    }
}
