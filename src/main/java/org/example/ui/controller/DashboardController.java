package org.example.ui.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import org.example.ui.NavigationService;
import org.example.util.ResourceUtil;

import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.scene.shape.SVGPath;
import javafx.scene.paint.Color;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Priority;
import javafx.animation.FillTransition;
import javafx.animation.FadeTransition;
import javafx.util.Duration;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.OverrunStyle;

public class DashboardController {

    private static final Logger LOGGER = Logger.getLogger(DashboardController.class.getName());

    @FXML private VBox sidebar;
    @FXML private Button menuDashboard;
    @FXML private Button menuUsers;
    @FXML private Button menuAttendance;
    @FXML private Button menuLogout;

    @FXML private StackPane contentPane;

    @FXML private ImageView inputLogo;

    // singleton-like reference for other controllers to call navigation
    private static DashboardController instance;

    private final NavigationService navigation = NavigationService.getInstance();

    // svg icons (graphics attached to buttons)
    private SVGPath iconDashboard, iconUsers, iconAttendance, iconLogout;

    @FXML
    public void initialize() {
        instance = this; // set instance

        // load default dashboard view
        loadDashboardView();

        // create vector icons and attach to buttons as graphics
        createAndAttachIcons();

        menuDashboard.setOnAction(e -> { setActiveNav(menuDashboard); loadDashboardView(); });
        menuUsers.setOnAction(e -> { setActiveNav(menuUsers); loadUsersView(); });
        menuAttendance.setOnAction(e -> { setActiveNav(menuAttendance); loadAttendanceView(); });
        menuLogout.setOnAction(e -> onLogout());

        // rely on CSS for hover/active visuals; do not apply inline hover styles that override CSS

        // set initial active after scene graph is ready so CSS is applied
        javafx.application.Platform.runLater(() -> {
            applyDesignSystem(sidebar);
            // ensure the global stylesheet is attached to the Scene so CSS selectors work
            try {
                if (sidebar != null && sidebar.getScene() != null) {
                    URL css = ResourceUtil.cssUrl("/css/user-profile.css");
                    String cssUrl = css.toExternalForm();
                    if (!sidebar.getScene().getStylesheets().contains(cssUrl)) sidebar.getScene().getStylesheets().add(cssUrl);
                    // reapply CSS and layout
                    sidebar.getScene().getRoot().applyCss();
                    sidebar.getScene().getRoot().layout();
                }
                // attempt to load a custom input logo from classpath /images/LOGO-INPUT.png
                try {
                    // bind contentPane to scene size so embedded pages fill available space
                    if (contentPane != null && sidebar != null && sidebar.getScene() != null) {
                        contentPane.prefWidthProperty().bind(sidebar.getScene().widthProperty().subtract(sidebar.widthProperty()).subtract(24));
                        contentPane.prefHeightProperty().bind(sidebar.getScene().heightProperty().subtract(24));
                    }
                    // bind inputLogo to sidebar width (responsive) and then load image
                    if (inputLogo != null && sidebar != null) {
                        inputLogo.setPreserveRatio(true);
                        // fit width = 80% of sidebar width, min 80 max sidebar width minus padding
                        inputLogo.fitWidthProperty().bind(sidebar.widthProperty().multiply(0.78));
                        inputLogo.fitHeightProperty().bind(sidebar.heightProperty().multiply(0.12));
                    }
                    loadSidebarLogo();
                } catch (Exception ignore) {}
            } catch (Exception ignored) {}

            setActiveNav(menuDashboard);
        });
    }

    public static DashboardController getInstance() {
        return instance;
    }

    private void clearContent() {
        contentPane.getChildren().clear();
    }

    private void loadDashboardView() {
        clearContent();
        try {
            URL u = ResourceUtil.fxml("/fxml/dashboard_cards.fxml");
            if (u != null) {
                Node n = FXMLLoader.load(u);
                contentPane.getChildren().add(n);
                // enforce design system style classes on dynamically loaded content
                javafx.application.Platform.runLater(() -> applyDesignSystem(n));
            }
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Failed to load dashboard cards view", ex);
        }
    }

    private void loadUsersView() {
        clearContent();
        try {
            URL u = ResourceUtil.fxml("/fxml/users.fxml");
            FXMLLoader loader = new FXMLLoader(u);
            Node n = loader.load();
            contentPane.getChildren().add(n);

            // ensure stylesheet is present and force CSS to re-apply
            javafx.application.Platform.runLater(() -> {
                try {
                    URL css = ResourceUtil.cssUrl("/css/user-profile.css");
                    String cssUrl = css.toExternalForm();
                    if (contentPane.getScene() != null && !contentPane.getScene().getStylesheets().contains(cssUrl)) {
                        contentPane.getScene().getStylesheets().add(cssUrl);
                    }
                    // force CSS recompute
                    if (contentPane.getScene() != null && contentPane.getScene().getRoot() != null) contentPane.getScene().getRoot().applyCss();
                    n.applyCss();
                    applyDesignSystem(n);
                } catch (Exception ignore) {}
            });

            // If controller exposes loadUsers(), call it to populate table on demand
            Object controller = loader.getController();
            if (controller instanceof org.example.ui.controller.UsersController) {
                ((org.example.ui.controller.UsersController) controller).loadUsers();
            }
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Failed to load users view", ex);
        }
    }

    private void loadAttendanceView() {
        clearContent();
        try {
            URL u = ResourceUtil.fxml("/fxml/attendance_list.fxml");
            if (u == null) return;
            Node n = FXMLLoader.load(u);
            contentPane.getChildren().add(n);

            // ensure stylesheet is present and force CSS to re-apply
            javafx.application.Platform.runLater(() -> {
                try {
                    URL css = ResourceUtil.cssUrl("/css/user-profile.css");
                    String cssUrl = css.toExternalForm();
                    if (contentPane.getScene() != null && !contentPane.getScene().getStylesheets().contains(cssUrl)) {
                        contentPane.getScene().getStylesheets().add(cssUrl);
                    }
                    if (contentPane.getScene() != null && contentPane.getScene().getRoot() != null) contentPane.getScene().getRoot().applyCss();
                    n.applyCss();
                    applyDesignSystem(n);
                } catch (Exception ignore) {}
            });

        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Failed to load attendance view", ex);
        }
    }

    // load user profile into contentPane (embedded page)
    public void loadUserProfile(long userId) {
        clearContent();
        try {
            URL u = ResourceUtil.fxml("/fxml/user_profile.fxml");
            if (u == null) return;
            FXMLLoader loader = new FXMLLoader(u);
            Parent n = loader.load();

            contentPane.getChildren().add(n);

            // attach page-specific stylesheet so styles apply without relying on FXML <stylesheets>
            try {
                URL css = ResourceUtil.cssUrl("/css/user-profile.css");
                String cssUrl = css.toExternalForm();
                // attach to the Scene after it's available
                javafx.application.Platform.runLater(() -> {
                    try {
                        if (contentPane != null && contentPane.getScene() != null) {
                            if (!contentPane.getScene().getStylesheets().contains(cssUrl))
                                contentPane.getScene().getStylesheets().add(cssUrl);
                            // force CSS recompute
                            if (contentPane.getScene().getRoot() != null) contentPane.getScene().getRoot().applyCss();
                            n.applyCss();
                            applyDesignSystem(n);
                        } else {
                            if (!n.getStylesheets().contains(cssUrl)) n.getStylesheets().add(cssUrl);
                        }
                    } catch (Exception ignore) {}
                });
            } catch (Exception ignore) {}

            Object controller = loader.getController();
            if (controller instanceof org.example.ui.controller.UserProfileController) {
                ((org.example.ui.controller.UserProfileController) controller).setUserId(userId);
            }
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Failed to load user profile view", ex);
        }
    }

    // public navigation helper so embedded pages can navigate back
    public void showUsersView() {
        setActiveNav(menuUsers);
        loadUsersView();
    }

    @FXML
    public void onMenuDashboard() {
        setActiveNav(menuDashboard);
        loadDashboardView();
    }

    @FXML
    public void onMenuUsers() {
        setActiveNav(menuUsers);
        loadUsersView();
    }

    @FXML
    public void onMenuAttendance() {
        setActiveNav(menuAttendance);
        loadAttendanceView();
    }

    @FXML
    public void onLogout() {
        // visually activate logout then perform logout logic
        setActiveNav(menuLogout);
        // clear current embedded content so controllers can stop/cleanup (e.g., stop webcam)
        clearContent();
        navigation.logout();
    }

    // helper to manage nav active state
    private void setActiveNav(Button activeBtn) {
         try {
             if (activeBtn == null) return;
             // remove active class from all nav buttons inside sidebar
             for (Node n : sidebar.getChildrenUnmodifiable()) {
                 if (n instanceof HBox) {
                     HBox hb = (HBox) n;
                     for (Node ch : hb.getChildren()) {
                         if (ch instanceof Button) {
                             ch.getStyleClass().remove("nav-button-active");
                         }
                         if (ch instanceof Region) {
                             if (ch.getStyleClass().contains("nav-accent")) ch.getStyleClass().remove("nav-accent-active");
                             if (ch.getStyleClass().contains("nav-icon")) ch.getStyleClass().remove("nav-icon-active");
                         }
                         // animate icon and accent reset for each HBox
                         // find svg graphic if Button
                         if (ch instanceof Button) {
                             Button b = (Button) ch;
                             if (b.getGraphic() instanceof SVGPath) {
                                 SVGPath svg = (SVGPath) b.getGraphic();
                                 FillTransition ft = new FillTransition(Duration.millis(220), svg);
                                 try { ft.setFromValue((Color) svg.getFill()); } catch (Exception ignore) {}
                                 ft.setToValue(Color.web("#2F6BFF"));
                                 ft.play();
                             }
                          }
                      }
                  }
              }
              // add active to the given button and its sibling accent region
              activeBtn.getStyleClass().add("nav-button-active");
              Node parent = activeBtn.getParent();
              if (parent instanceof HBox) {
                  HBox hb = (HBox) parent;
                  for (Node ch : hb.getChildren()) {
                     if (ch instanceof Region) {
                         if (ch.getStyleClass().contains("nav-accent")) ch.getStyleClass().add("nav-accent-active");
                         if (ch.getStyleClass().contains("nav-icon")) ch.getStyleClass().add("nav-icon-active");
                     }
                  }
              }

             // animate active icon fill and accent slide
            if (activeBtn.getGraphic() instanceof SVGPath) {
                SVGPath svg = (SVGPath) activeBtn.getGraphic();
                FillTransition ft = new FillTransition(Duration.millis(260), svg);
                try { ft.setFromValue((Color) svg.getFill()); } catch (Exception ignore) {}
                ft.setToValue(Color.WHITE);
                ft.play();
            }
             // animate accent on the active HBox (fade)
             if (parent instanceof HBox) {
                 for (Node ch : ((HBox) parent).getChildren()) {
                     if (ch instanceof Region && ch.getStyleClass().contains("nav-accent")) {
                         FadeTransition f = new FadeTransition(Duration.millis(240), ch);
                         f.setFromValue(0.0);
                         f.setToValue(1.0);
                         f.play();
                     }
                 }
             }
          } catch (Exception ex) {
              LOGGER.log(Level.SEVERE, "Error updating navigation active state", ex);
          }
     }

    // Walk node tree and ensure style classes are present for common controls
    private void applyDesignSystem(Node root) {
        try {
            if (root == null) return;
            // recursive traversal
            java.util.Deque<Node> stack = new java.util.ArrayDeque<>();
            stack.push(root);
            while (!stack.isEmpty()) {
                Node n = stack.pop();
                // Buttons
                if (n instanceof Button) {
                    Button b = (Button) n;
                    java.util.List<String> sc = b.getStyleClass();
                    // if no btn-* present, decide default based on id/text
                    boolean hasBtnClass = sc.stream().anyMatch(s -> s.startsWith("btn-"));
                    if (!hasBtnClass) {
                        String id = b.getId() == null ? "" : b.getId().toLowerCase();
                        String text = b.getText() == null ? "" : b.getText().toLowerCase();
                        if (id.contains("save") || id.contains("upload") || text.contains("save") || text.contains("upload") || text.contains("dodaj") || text.contains("export") || text.contains("primijeni")) sc.add("btn-primary");
                        else sc.add("btn-secondary");
                    }
                }

                // TableView
                if (n instanceof TableView) {
                    TableView<?> tv = (TableView<?>) n;
                    if (!tv.getStyleClass().contains("table-modern")) tv.getStyleClass().add("table-modern");
                    if (!tv.getStyleClass().contains("table-card") && tv.getParent() != null && tv.getParent().getStyleClass().contains("card")) tv.getStyleClass().add("table-card");

                    // Make columns share the full table width and be stable (proportional).
                    try {
                        @SuppressWarnings("unchecked")
                        TableView<Object> tvObj = (TableView<Object>) (TableView<?>) tv;
                        int colCount = tvObj.getColumns().size();
                        if (colCount > 0) {
                            // Use constrained resize policy so columns span table width
                            tvObj.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
                            // bind each column width to table width / count and disable user resizing
                            for (TableColumn<Object, ?> col : tvObj.getColumns()) {
                                col.setResizable(false);
                                col.prefWidthProperty().bind(tvObj.widthProperty().divide(colCount));
                            }

                            // Accent first column cells by applying a cellFactory that adds a style class
                            @SuppressWarnings({"unchecked","rawtypes"})
                            TableColumn<Object,Object> firstCol = (TableColumn) tvObj.getColumns().get(0);
                            firstCol.setCellFactory(c -> new TableCell<>() {
                                @Override
                                protected void updateItem(Object item, boolean empty) {
                                    super.updateItem(item, empty);
                                    // clear then conditionally add class
                                    getStyleClass().remove("first-column-cell");
                                    if (!empty) {
                                        getStyleClass().add("first-column-cell");
                                        setText(item == null ? "" : item.toString());
                                    } else {
                                        setText("");
                                    }
                                }
                            });

                            // Do NOT mark any data row as header; header styling is handled via CSS on column headers
                        }
                    } catch (Exception ignore) {}
                 }

                // Text inputs
                if (n instanceof TextInputControl) {
                    TextInputControl tic = (TextInputControl) n;
                    if (!tic.getStyleClass().contains("input")) tic.getStyleClass().add("input");
                }

                // ComboBox / ChoiceBox
                if (n instanceof ComboBoxBase) {
                    if (!n.getStyleClass().contains("input")) n.getStyleClass().add("input");
                }
                if (n instanceof DatePicker) {
                    if (!n.getStyleClass().contains("input")) n.getStyleClass().add("input");
                    if (!n.getStyleClass().contains("date-picker")) n.getStyleClass().add("date-picker");
                }

                // recurse into children if Parent
                if (n instanceof javafx.scene.Parent) {
                    javafx.scene.Parent p = (javafx.scene.Parent) n;
                    for (Node child : p.getChildrenUnmodifiable()) stack.push(child);
                }
            }
            // force CSS recompute on root and scene
            root.applyCss();
            if (contentPane != null && contentPane.getScene() != null && contentPane.getScene().getRoot() != null) contentPane.getScene().getRoot().applyCss();
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Error applying design system to node tree", ex);
        }
    }

    // create SVG icons and attach them as graphics to nav buttons
    private void createAndAttachIcons() {
        iconDashboard = new SVGPath();
        iconDashboard.setContent("M3 13h8V3H3v10zm10 8h8V11h-8v10zm0-18v8h8V3h-8zM3 21h8v-8H3v8z");

        iconUsers = new SVGPath();
        iconUsers.setContent("M16 11c1.66 0 3-1.34 3-3s-1.34-3-3-3-3 1.34-3 3 1.34 3 3 3zM8 11c1.66 0 3-1.34 3-3S9.66 5 8 5 5 6.34 5 8s1.34 3 3 3z");

        iconAttendance = new SVGPath();
        iconAttendance.setContent("M19 3h-1V1h-2v2H8V1H6v2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 16H5V8h14v11zM7 10h5v5H7z");

        iconLogout = new SVGPath();
        iconLogout.setContent("M14 7v4h-4v2h4v4l5-5-5-5zM5 5h6v2H7v10h4v2H5c-1.1 0-2-.9-2-2V7c0-1.1.9-2 2-2z");

        // style icons (blue color) and attach as graphics
        for (SVGPath svg : new SVGPath[]{iconDashboard, iconUsers, iconAttendance, iconLogout}) {
            svg.setFill(Color.web("#2F6BFF")); // blue accent
            svg.setScaleX(0.7);
            svg.setScaleY(0.7);
            svg.setSmooth(true);
        }

        // ensure labels are explicitly set (defensive: FXML sometimes loses text if modified)
        menuDashboard.setText("Dashboard");
        menuUsers.setText("Korisnici");
        menuAttendance.setText("Prisustva");
        menuLogout.setText("Logout");

        // attach as graphics and ensure button text is visible and buttons expand
        attachGraphicToButton(menuDashboard, iconDashboard);
        attachGraphicToButton(menuUsers, iconUsers);
        attachGraphicToButton(menuAttendance, iconAttendance);
        attachGraphicToButton(menuLogout, iconLogout);
    }

    // helper to attach SVG graphic and configure button layout and hover class
    private void attachGraphicToButton(Button btn, SVGPath svg) {
        // create an HBox graphic: svg + label so the label is a real node (no ellipsis from Button text)
         javafx.scene.control.Label lbl = new javafx.scene.control.Label(btn.getText());
         lbl.setTextFill(Color.web("#07112b"));
         lbl.setStyle("-fx-font-weight: 600; -fx-font-size: 14px;");
         lbl.setMaxWidth(Double.MAX_VALUE);
         lbl.setTextOverrun(OverrunStyle.CLIP);
         HBox graphicBox = new HBox(10, svg, lbl);
         graphicBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        // allow the label to grow so text is visible (prevents ellipsis)
        HBox.setHgrow(lbl, Priority.ALWAYS);

        btn.setGraphic(graphicBox);
        btn.setContentDisplay(ContentDisplay.LEFT);
        btn.setMinWidth(160);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setPrefWidth(Double.MAX_VALUE);
        btn.setText(""); // clear button text to avoid duplication
        HBox.setHgrow(btn, Priority.ALWAYS);

        // hover toggles a class so CSS can respond reliably
        btn.hoverProperty().addListener((obs, oldV, newV) -> {
            if (newV) btn.getStyleClass().add("nav-hover"); else btn.getStyleClass().remove("nav-hover");
        });
     }

    // Load sidebar input logo from classpath /images/LOGO-INPUT.png if present
    private void loadSidebarLogo() {
        try {
            if (inputLogo == null) return;
            try {
                Image img = ResourceUtil.image("/images/LOGO-INPUT.png");
                javafx.application.Platform.runLater(() -> {
                    inputLogo.setImage(img);
                    inputLogo.setVisible(true);
                });
            } catch (Exception e) {
                // hide the ImageView if the resource isn't bundled
                javafx.application.Platform.runLater(() -> inputLogo.setVisible(false));
            }
        } catch (Exception ex) {
            LOGGER.log(Level.FINE, "Could not load sidebar logo", ex);
        }
    }
}
