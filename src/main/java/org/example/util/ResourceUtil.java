package org.example.util;

import javafx.scene.image.Image;

import java.net.URL;
import java.util.Objects;

public class ResourceUtil {

    /**
     * Return URL for an FXML/CSS/resource path on the classpath. Path must start with '/'.
     * Throws a runtime exception with clear message if resource missing.
     */
    public static URL resourceUrl(String path) {
        Objects.requireNonNull(path, "path must not be null");
        if (!path.startsWith("/")) throw new RuntimeException("Resource path must start with '/': " + path);
        URL u = ResourceUtil.class.getResource(path);
        if (u == null) throw new RuntimeException("Missing resource: " + path);
        return u;
    }

    public static URL fxml(String path) {
        return resourceUrl(path);
    }

    public static Image image(String path) {
        Objects.requireNonNull(path, "path must not be null");
        if (!path.startsWith("/")) throw new RuntimeException("Image path must start with '/': " + path);
        try (var is = ResourceUtil.class.getResourceAsStream(path)) {
            if (is == null) throw new RuntimeException("Missing resource: " + path);
            return new Image(is);
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load image resource: " + path, e);
        }
    }

    public static URL cssUrl(String path) {
        return resourceUrl(path);
    }
}

