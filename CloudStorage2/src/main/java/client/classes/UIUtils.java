package client.classes;

import javafx.scene.control.Alert;
import javafx.stage.StageStyle;

import static javafx.application.Platform.*;
import static javafx.scene.control.Alert.AlertType.ERROR;
import static javafx.scene.control.ButtonType.OK;

public final class UIUtils {

    public static void showError(String text) {
        updateUI(() -> {
            Alert alert = new Alert(ERROR, text, OK);
            alert.initStyle(StageStyle.UTILITY);
            alert.setHeaderText(null);
            alert.showAndWait();
        });
    }


    public static void showError(Exception excpt) {
        showError(excpt.getMessage());
    }


    public static void updateUI(Runnable task) {
        if (isFxApplicationThread())
            task.run();
        else
            runLater(task);
    }
}
