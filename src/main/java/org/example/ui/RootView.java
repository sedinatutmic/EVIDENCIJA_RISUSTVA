package org.example.ui;

import javafx.scene.Node;

public interface RootView {
    void setContent(Node node);
    void showPublicMode();
    void showAdminMode();
    void logout();
    void setTopToolbarVisible(boolean visible);
}
