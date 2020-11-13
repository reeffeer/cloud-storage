package client.classes;

import commonClasses.*;

import java.io.File;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.PriorityBlockingQueue;

import static java.lang.Thread.currentThread;

public class FileSender implements Runnable {

    private final NetConnection conn;
    private final PriorityBlockingQueue<File> queue;
    private static final int MAX_COUNT = 100;

    public FileSender() {
        conn = Main.getNetConnection();
        queue = new PriorityBlockingQueue<>(MAX_COUNT, Comparator.comparingLong(File::length));
    }

    @Override
    public void run() {
        try {
            while (!currentThread().isInterrupted()) {
                Path path = queue.take().toPath();

                FileSendOptimizer.sendFile(path,
                        dataPackage -> {
                            try {
                                conn.sendToServer(dataPackage);
                            }
                            catch (NetConnection.SendDataException e) {
                                e.printStackTrace();
                            }
                        });
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void addFiles(List<File> files) {
        files.forEach(queue::put);
    }

}