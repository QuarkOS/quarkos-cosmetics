package dev.quarkos.cosmetics.model;

public final class ModelException extends Exception {
    public final String path;

    public ModelException(String path, String message) {
        super(message);
        this.path = path;
    }
}
