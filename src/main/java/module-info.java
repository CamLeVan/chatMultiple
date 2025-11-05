module com.fragmented.download.chatnew {
    requires javafx.controls;
    requires javafx.fxml;


    opens com.fragmented.download.chatnew to javafx.fxml;
    exports com.fragmented.download.chatnew;
}