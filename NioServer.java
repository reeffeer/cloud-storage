package nio;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Set;

public class NioServer {

    private final ByteBuffer buffer = ByteBuffer.allocate(1024);
    private String rootPath = "Server";

    public NioServer() throws IOException {
        ServerSocketChannel server = ServerSocketChannel.open();
        server.bind(new InetSocketAddress(8189));
        server.configureBlocking(false);
        Selector selector = Selector.open();
        server.register(selector, SelectionKey.OP_ACCEPT);
        System.out.println("Server started successfully!");

        while (selector.isOpen()) {
            selector.select();
            Set<SelectionKey> keys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = keys.iterator();
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                if (key.isAcceptable()) {
                    handleAccept(key, selector);
                }
                if (key.isReadable()) {
                    handleRead(key, selector);
                }
                iterator.remove();
            }
        }
    }

    private void handleAccept(SelectionKey key, Selector selector) throws IOException {
        SocketChannel channel = ((ServerSocketChannel) key.channel()).accept();
        channel.configureBlocking(false);
        System.out.println("Client accepted. IP: " + channel.getRemoteAddress());
        channel.register(selector, SelectionKey.OP_READ);
        channel.write(ByteBuffer.wrap(("Enter --help for read commands" + System.lineSeparator()).getBytes()));
    }

    private void handleRead(SelectionKey key, Selector selector) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        int read = channel.read(buffer);
        if (read == -1) {
            channel.close();
            return;
        } else if (read == 0) {
            return;
        }
        buffer.flip();
        StringBuilder sb = new StringBuilder();
        while (buffer.hasRemaining()) {
            sb.append((char) buffer.get());
        }
        String command = sb.toString().replace(System.lineSeparator(), "");
        buffer.clear();
        System.out.println(command);
        if (command.equals("--help")) {
            channel.write(ByteBuffer.wrap(("ls - for show list of dirs/files" + System.lineSeparator() +
                    "cd - for change directory" + System.lineSeparator() +
                    "touch - create file" + System.lineSeparator() +
                    "mkdir - create directory" + System.lineSeparator() +
                    "rm - delete directory or file" + System.lineSeparator() +
                    "cat - read file" + System.lineSeparator() +
                    "copy - copy [filename.***] [fullPath/../filename.***] - args in []" + System.lineSeparator()
            ).getBytes()));
        } else if (command.equals("ls")) {
            channel.write(ByteBuffer.wrap(("----------" + System.lineSeparator() + getFilesList() + System.lineSeparator() + "----------" + System.lineSeparator()).getBytes()));
        } else if (command.matches("cd .*") && command.split(" ").length == 2) {
            changeRootPath(command, channel);
        } else if (command.matches("touch [\\w]+\\.[a-zA-Z]+$")) {
            if (createFile(command.split(" ")[1])) {
                channel.write(ByteBuffer.wrap(("File successfully created!" + System.lineSeparator()).getBytes()));
            } else {
                channel.write(ByteBuffer.wrap(("File already exists or something went wrong" + System.lineSeparator()).getBytes()));
            }
        } else if (command.matches("mkdir [\\w]+$")) {
            if (makeDirectory(command.split(" ")[1])) {
                channel.write(ByteBuffer.wrap(("Directory successfully created!" + System.lineSeparator()).getBytes()));
            } else {
                channel.write(ByteBuffer.wrap(("Directory already exists or something went wrong" + System.lineSeparator()).getBytes()));
            }
        } else if (command.matches("rm [\\w]+(\\.[a-zA-Z]+)?$")) {
            if (delete(command.split(" ")[1])) {
                channel.write(ByteBuffer.wrap(("Successfully deleted!" + System.lineSeparator()).getBytes()));
            } else {
                channel.write(ByteBuffer.wrap(("Doesn't exists or directory isn't empty or something else went wrong" + System.lineSeparator()).getBytes()));
            }
        } else if (command.matches("cat [\\w]+\\.[a-zA-Z]+$")) {
            channel.write(ByteBuffer.wrap((readFile(command.split(" ")[1])).getBytes()));
        } else if (command.matches("copy [\\w]+\\.[a-zA-Z]+ ([\\w]+/)*[\\w]+\\.[a-zA-Z]+$")) {
            if (copyFile(command.split(" ")[1], command.split(" ")[2])) {
                channel.write(ByteBuffer.wrap(("Copy successfully completed" + System.lineSeparator()).getBytes()));
            } else {
                channel.write(ByteBuffer.wrap(("Directory doesn't exists or something went wrong" + System.lineSeparator()).getBytes()));
            }
        } else if (command.length() != 0) {
            channel.write(ByteBuffer.wrap(("Enter --help for read commands" + System.lineSeparator()).getBytes()));
        }
    }

    private boolean copyFile(String fileName, String dst) throws IOException {
        if (dst.contains("/") && !new File(dst.substring(0, dst.lastIndexOf("/"))).exists()) {
            return false;
        } else {
            Files.copy(Paths.get(rootPath + "/" + fileName), Paths.get("Server/" + dst));
            return true;
        }
    }

    private String readFile(String fileName) throws IOException {
        StringBuilder result = new StringBuilder();
        Files.newBufferedReader(Paths.get(rootPath + "/" + fileName))
                .lines()
                .forEach(t -> result.append(t).append(System.lineSeparator()));
        return result.toString();
    }

    private boolean delete(String name) {
        return new File(rootPath + "/" + name).delete();
    }

    private boolean makeDirectory(String dir) {
        return new File(rootPath + "/" + dir).mkdir();
    }

    private boolean createFile(String fileName) throws IOException {
        return new File(rootPath + "/" + fileName).createNewFile();
    }

    private void changeRootPath(String command, SocketChannel channel) throws IOException {
        String tmpPath = rootPath;
        String[] path = command.split(" ")[1].split("/");
        for (String s : path) {
            if (s.equals("..") && !tmpPath.equalsIgnoreCase("Server")) {
                tmpPath = tmpPath.substring(0, tmpPath.lastIndexOf("/"));
                System.out.println(tmpPath);
            } else if (s.equals("..") || s.matches(".*[^\\w].*")) {
                channel.write(ByteBuffer.wrap(("Invalid destination path" + System.lineSeparator()).getBytes()));
                return;
            } else if (new File(tmpPath + "/" + s).exists()) {
                tmpPath = tmpPath + "/" + s;
            } else {
                channel.write(ByteBuffer.wrap(("Invalid destination path" + System.lineSeparator()).getBytes()));
                return;
            }
        }
        rootPath = tmpPath;
    }

    private String getFilesList() {
        String[] files = new File(rootPath).list();
        if (files != null) {
            return String.join(", ", files);
        } else {
            return "";
        }
    }

    public static void main(String[] args) throws IOException {
        new NioServer();
    }
}
