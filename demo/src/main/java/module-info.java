module com.praksa.demo {
    requires javafx.controls;
    requires javafx.fxml;


    opens com.praksa.demo to javafx.fxml;
    exports com.praksa.demo;
}