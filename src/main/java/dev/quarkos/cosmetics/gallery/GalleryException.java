package dev.quarkos.cosmetics.gallery;

public final class GalleryException extends Exception {
    public final int status;
    public final String error;

    public GalleryException(int status, String error, String message) {
        super(message);
        this.status = status;
        this.error = error;
    }
}
