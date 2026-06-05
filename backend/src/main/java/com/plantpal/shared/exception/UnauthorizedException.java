package com.plantpal.shared.exception;

public class UnauthorizedException extends PlantPalException {

    private static final int ERROR_CODE = 401;

    public UnauthorizedException(String message) {
        super(message, ERROR_CODE);
    }
}
