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
        // call onHidden for current controller to stop camera/resources
        if (currentController instanceof ViewLifecycle) {
            try {
                ((ViewLifecycle) currentController).onHidden();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        Platform.runLater(() -> {
            try {
                URL u = getClass().getResource("/fxml/login.fxml");
                if (u == null) return;
                FXMLLoader loader = new FXMLLoader(u);
                javafx.scene.Parent root = loader.load();
                javafx.stage.Stage stage = new javafx.stage.Stage();
                stage.setTitle("Prijava administratora");
                if (rootView != null && rootView instanceof javafx.scene.Node) {
                    // unlikely; rootView is not a Node. Instead try to get owner from RootController via reflection
                }
                stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
                stage.setScene(new javafx.scene.Scene(root));

                Object ctrl = loader.getController();
                try {
                    java.lang.reflect.Method m1 = ctrl.getClass().getMethod("setNavigation", NavigationService.class);
                    java.lang.reflect.Method m2 = ctrl.getClass().getMethod("setParentStage", javafx.stage.Stage.class);
                    if (m1 != null) m1.invoke(ctrl, this);
                    if (m2 != null) m2.invoke(ctrl, stage);
                } catch (NoSuchMethodException nsm) {
                    // not critical
                }

                stage.showAndWait();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
    }
}
