package client.classes;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;

import static client.classes.Main.STORAGE_DIR;
import static client.classes.UIUtils.showError;
import static client.classes.UIUtils.updateUI;
import static java.lang.System.exit;
import static java.util.concurrent.Executors.newFixedThreadPool;
import static java.util.stream.Collectors.toList;


public class Controller implements Initializable {

    private NetConnection conn;
    private Window parentWin;
    private ServerResponseHandler responseHandler;
    private FileSender fileSender;
    private ExecutorService exec;

    @FXML
    HBox rootElem;
    @FXML
    Button btnConn;
    @FXML
    Button btnDisconn;
    @FXML
    Button btnExit;

    @FXML
    Button btnSendFiles;
    @FXML
    Button btnDeleteLocalFiles;
    @FXML
    ListView<String> lstLocalFiles;

    @FXML
    Button btnDownloadFiles;
    @FXML
    Button btnDeleteFilesInCloud;
    @FXML
    ListView<String> lstFilesInCloud;

    private static final int THREAD_MAX_COUNT = 2;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        conn = Main.getNetConnection();

        responseHandler = new ServerResponseHandler();
        responseHandler.setFileListActionUI(this::updateFileListFromCloud);
        responseHandler.setFileDataActionUI(this::updateLocalFileList);
        fileSender = new FileSender();
        exec = newFixedThreadPool(THREAD_MAX_COUNT);
        exec.execute(responseHandler);
        exec.execute(fileSender);

        btnConn.managedProperty().bind(btnConn.visibleProperty());
        btnDisconn.managedProperty().bind(btnDisconn.visibleProperty());
        btnConn.setVisible(false);
        btnConn.setDisable(true);

        updateLocalFileList();
        getFileListFromCloud();
    }

    public void connectToCloud(ActionEvent event) {
        try {
            conn.open();
            exec.execute(responseHandler);
            exec.execute(fileSender);
            setButtonsState(true);
        }
        catch (IOException e) {
            showError("connection failed");
        }
    }

    public void disconnectFromCloud(ActionEvent event) {
        try {
            conn.close();
            setButtonsState(false);
        }
        catch (IOException e) {
            showError("error occurred on disconnecting");
        }
    }

    private void setButtonsState(boolean connected) {
        btnConn.setVisible(!connected);
        btnConn.setDisable(connected);
        btnDisconn.setDisable(!connected);
        btnDisconn.setVisible(connected);

        btnSendFiles.setDisable(!connected);
        btnDownloadFiles.setDisable(!connected);
        btnDeleteFilesInCloud.setDisable(!connected);
    }

    public void getFileListFromCloud() {
        try {
            conn.sendFileListCommand();
        }
        catch (NetConnection.SendDataException e) {
            showError(e);
        }
    }

    public void sendFiles(ActionEvent event) {
        if (parentWin == null) parentWin = rootElem.getScene().getWindow();

        FileChooser fc = new FileChooser();
        List<File> files = fc.showOpenMultipleDialog(parentWin);

        if (files == null || files.isEmpty())
            return;

        fileSender.addFiles(files);
    }

    public void deleteLocalFiles(ActionEvent event) {
        try {
            MultipleSelectionModel<String> model = lstLocalFiles.getSelectionModel();
            List<String> items = model.getSelectedItems();

            for (String fn : items) {
                Path path = Paths.get(STORAGE_DIR + "/" + fn);
                Files.delete(path);
                updateLocalFileList();
            }
        }
        catch (IOException e) {
            showError(e);
        }
    }

    public void downloadFiles(ActionEvent event) {
        try {
            MultipleSelectionModel<String> model = lstFilesInCloud.getSelectionModel();
            List<String> items = model.getSelectedItems();
            conn.sendDownloadFilesCommand(items);
        }
        catch (NetConnection.SendDataException e) {
            showError(e);
        }
    }

    public void deleteFilesInCloud(ActionEvent event) {
        try {
            MultipleSelectionModel<String> model = lstFilesInCloud.getSelectionModel();
            List<String> items = model.getSelectedItems();
            conn.sendDeleteFilesCommand(items);
        }
        catch (NetConnection.SendDataException e) {
            showError(e);
        }
    }

    public void updateFileListFromCloud(List<String> filenames) {
        updateUI(() -> {
            List<String> items = lstFilesInCloud.getItems();
            items.clear();
            items.addAll(filenames);
        });
    }

    public void updateLocalFileList() {
        updateUI(() -> {
            try {
                List<String> fnames = Files.list(Paths.get(STORAGE_DIR))
                        .map(x -> x.getFileName().toString())
                        .collect(toList());

                List<String> items = lstLocalFiles.getItems();
                items.clear();
                items.addAll(fnames);
            }
            catch (IOException e) {
                showError(e);
            }
        });
    }

    public void exitFromApp(ActionEvent event) {
        exit(0);
    }

}
