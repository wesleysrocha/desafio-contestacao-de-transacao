package com.desafio.prevencao.middleware.exception;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String resourceName, String id) {
        super(resourceName + " não encontrado com id: " + id);
    }
}
