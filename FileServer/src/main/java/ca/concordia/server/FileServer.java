package ca.concordia.server;

import ca.concordia.filesystem.FileSystemManager;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class FileServer {

    private final FileSystemManager fsManager;
    private final int port;

    public FileServer(int port, String fileSystemName, int totalSize) throws FileNotFoundException {
        this.fsManager = new FileSystemManager(fileSystemName, totalSize);
        this.port = port;
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server started. Listening on port " + port);

            while (true) {
                final Socket client = serverSocket.accept();

                Thread t = new Thread(new ClientHandler(client, fsManager));
                t.start();
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }

    private static class ClientHandler implements Runnable {

        private final Socket client;
        private final FileSystemManager fs;

        ClientHandler(Socket client, FileSystemManager fs) {
            this.client = client;
            this.fs = fs;
        }

        @Override
        public void run() {
            try (
                BufferedReader br = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
                PrintWriter pw = new PrintWriter(client.getOutputStream(), true)
            ) {
                String line;
                while ((line = br.readLine()) != null) {    //Reads one line at a time from the client’s input stream
                    line = line.trim();
                    if (line.isEmpty()) continue;

                    String[] parts = line.split(" ", 3);    //Splits the line by spaces into parts. Example: "CREATE myfile.txt" -> ["CREATE", "myfile.txt"]
                    String cmd = parts[0].toUpperCase();    //Automatically assigns a string command to be used later in the swtich.

                    try {
                        switch (cmd) {
                            case "CREATE" -> {
                                // First checks if file name is too large, if it isn't it try to create the file by calling the fsManager which is located in FileSystemManager.java, and it catches any exception which is part of the function.
                                if (parts.length < 2) { 
                                    pw.println("ERROR: Missing filename"); break; 
                                }
                                String name = parts[1];
                                if (name.length() > 11) { 
                                    pw.println("ERROR: filename too large"); break; 
                                }
                                fs.createFile(name);
                                pw.println("SUCCESS");
                            }
                            case "WRITE" -> {
                                if (parts.length < 3) { 
                                    pw.println("ERROR: Missing filename or content"); break; 
                                }  
                                String name = parts[1]; 
                                String content = parts[2];//takes all data after name
                                fs.writeFile(name, content.getBytes(StandardCharsets.UTF_8));
                                pw.println("SUCCESS");
                            }
                            case "READ" -> {
                                if (parts.length < 2) { pw.println("ERROR: Missing filename"); break; }
                                byte[] data = fs.readFile(parts[1]);
                                pw.println(new String(data, StandardCharsets.UTF_8));
                            }
                            case "DELETE" -> {
                                if (parts.length < 2) { pw.println("ERROR: Missing filename"); break; }
                                fs.deleteFile(parts[1]);
                                pw.println("SUCCESS");
                            }
                            case "LIST" -> {
                                List<String> out = fs.listFiles();
                                pw.println(String.join(" ", out));
                            }
                            case "DISCONNECT" -> {
                                pw.println("BYE");
                                return;
                            }
                            default -> pw.println("ERROR: Unknown command");
                        }
                    } catch (Exception e) {
                        pw.println("ERROR: " + e.getMessage());
                    }
                }
            } catch (IOException ignored) {}
        }
    }
}
