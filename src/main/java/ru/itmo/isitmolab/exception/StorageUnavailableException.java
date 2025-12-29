package ru.itmo.isitmolab.exception;

public class StorageUnavailableException extends RuntimeException {
    public StorageUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
    public StorageUnavailableException(String message) {
        super(message);
    }
}
