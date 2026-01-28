package org.example.ui;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;

import java.net.URL;

/**
 * Simple navigation service that loads FXML into RootView's content pane
 * and invokes lifecycle hooks on controllers that implement ViewLifecycle.
 */
public class NavigationService {
    private static NavigationService instance;
    private RootView rootView;
    private Object currentController;

    private NavigationService() {}

    public static synchronized NavigationService getInstance() {
        if (instance == null) instance = new NavigationService();
        return instance;
    }

    public void setRootView(RootView rootView) {
        this.rootView = rootView;
    }

    public RootView getRootView() {
        return rootView;
    }

    public void showPublicMode() {
        if (rootView != null) rootView.showPublicMode();
    }

    public void showAdminMode() {
        if (rootView != null) rootView.showAdminMode();
    }

    public void logout() {
        if (rootView != null) rootView.logout();
    }

    /**
     * Load fxml and place it into the center content pane. Calls onHidden/onShown
     * on controllers that implement ViewLifecycle.
     */
    public void navigateTo(String fxmlPath) {
        if (rootView == null) return;
        Platform.runLater(() -> {
            try {
                // call onHidden for current controller
                if (currentController instanceof ViewLifecycle) {
                    ((ViewLifecycle) currentController).onHidden();
                }

                URL u = getClass().getResource(fxmlPath);
                if (u == null) {
                    System.err.println("FXML not found: " + fxmlPath);
                    return;
                }
                FXMLLoader loader = new FXMLLoader(u);
                Node n = loader.load();
                Object controller = loader.getController();

                // place into root
                rootView.setContent(n);

                // call onShown for new controller
                if (controller instanceof ViewLifecycle) {
                    ((ViewLifecycle) controller).onShown();
                }

                // Set toolbar visibility: only show toolbar on the QR scanner public page
                try {
                    if (rootView != null) {
                        boolean showToolbar = "/fxml/qrscan.fxml".equals(fxmlPath);
                        rootView.setTopToolbarVisible(showToolbar);
                    }
                } catch (Exception ignore) {
                }

                currentController = controller;
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
    }

    /**
     * Show the login dialog as a modal. Before showing, notify the current controller
     * that it is being hidden so it can stop resources like the camera.
     */
    public void showLoginDialog() {
        // previous behavior opened a modal dialog; replace with navigation to admin_login.fxml
        if (currentController instanceof ViewLifecycle) {
            try {
                ((ViewLifecycle) currentController).onHidden();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        // navigate to admin login page inside the main content area
        navigateTo("/fxml/admin_login.fxml");
    }
}
