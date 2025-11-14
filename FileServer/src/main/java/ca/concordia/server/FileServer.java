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

    private FileSystemManager fsManager;
    private int port;

    public FileServer(int port, String fileSystemName, int totalSize) throws FileNotFoundException {
        // Initialize the FileSystemManager
        this.fsManager = new FileSystemManager(fileSystemName, totalSize);
        this.port = port;
    }

    public void start() {

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server started. Listening on port " + port);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Handling client: " + clientSocket);
                try (
                        BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream())); PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true)) {
                    String line;
                    while ((line = reader.readLine()) != null) {           //Reads one line at a time from the client’s input stream 
                        System.out.println("Received from client: " + line);
                        String[] parts = line.split(" ");           //Splits the line by spaces into parts. Example: "CREATE myfile.txt" -> ["CREATE", "myfile.txt"]
                        String command = parts[0].toUpperCase();           //Automatically assigns a string command to be used later in the swtich.

                        switch (command) {
                            case "CREATE" -> {
                                // First checks if file name is too large, if it isn't it try to create the file by calling the fsManager which is located in FileSystemManager.java, and it catches any exception which is part of the function.
                                if (parts[1].length() > 11) {
                                    writer.println("ERROR: filename too large");
                                    break;
                                }
                                try {
                                    fsManager.createFile(parts[1]);
                                    writer.println("SUCCESS: File '" + parts[1] + "' created.");
                                } catch (Exception e) {
                                    writer.println("ERROR: " + e.getMessage());
                                }
                            }

                            case "WRITE" -> {
                                String filename = parts[1];
                                String content = line.substring(command.length() + filename.length() + 2);//takes all data after name
                                List<String> allFiles = fsManager.listFiles();

                                if (!allFiles.contains(filename)) {
                                    writer.println("ERROR: file <'" + filename + "'> does not exist");
                                    break;
                                }

                                try {
                                    fsManager.writeFile(filename, content.getBytes(StandardCharsets.UTF_8));//corrected file type
                                    writer.println("SUCCESS: wrote to file <'" + filename + "'>."); // NOTE: THIS LINE IS NOT SPECIFIED IN THE ASSIGNMENT INSTRUCTIONS BUT I FIND IT NESSISARY SO I WROTE IT.
                                } catch (OutOfMemoryError e) {                    // TO ADDRESS CATCH ERROR
                                    writer.println("ERROR: file too large");
                                } catch (Exception e) {
                                    writer.println("ERROR: " + e.getMessage());
                                }
                            }

                            case "READ" -> {
                                String filename = parts[1];
                                List<String> allFilesR = fsManager.listFiles();
                                if (!allFilesR.contains(filename)) {
                                    writer.println("ERROR: file <'" + filename + "'> does not exist");
                                    break;
                                }
                                try {
                                    byte[] data = fsManager.readFile(filename);
                                    writer.println("SUCCESS: read the following from file '" + filename + "'>.");
                                    writer.println(new String(data, StandardCharsets.UTF_8));
                                } catch (Exception e) {
                                    writer.println("ERROR: " + e.getMessage());
                                }
                            }

                            case "DELETE" -> {
                                String filename = parts[1];
                                List<String> allFilesD = fsManager.listFiles();
                                if (!allFilesD.contains(filename)) {
                                    writer.println("ERROR: file <'" + filename + "'> does not exist");
                                    break;
                                }
                                try {
                                    fsManager.deleteFile(filename);
                                    writer.println("SUCCESS: Deleted file '" + filename + "'.");
                                } catch (Exception e) {
                                    writer.println("ERROR: " + e.getMessage());
                                }
                            }

                            case "LIST" -> {
                                try {
                                    List<String> files = fsManager.listFiles();
                                    writer.println(String.join(" ", files));
                                } catch (Exception e) {
                                    writer.println("");
                                }
                            }
                            case "QUIT" -> {
                                writer.println("SUCCESS: Disconnecting.");
                                return;
                            }
                            default -> writer.println("ERROR: Unknown command.");
                        }
                    }
                } catch (Exception e) {
                } finally {
                    try {
                        clientSocket.close();
                    } catch (IOException e) {
                        // Ignore
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Could not start server on port " + port);
        }
    }

}
