package Sockets;

import java.io.*;
import java.net.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Scanner;

public class Server {
    private static final int PORT = 54321;
    private static CopyOnWriteArrayList<ClientHandler> clients = new CopyOnWriteArrayList<>();

    public static void main(String[] args) {
        try {
            ServerSocket serverSocket = new ServerSocket(PORT);
            System.out.println("Server is running and waiting for connections...");

            // Thread to handle server admin input
            new Thread(() -> {
                Scanner scanner = new Scanner(System.in);
                while (true) {
                    String serverMessage = scanner.nextLine();
                    broadcast("[Server]: " + serverMessage, null);
                }
            }).start();

            // Accept incoming connections
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected: " + clientSocket);

                ClientHandler clientHandler = new ClientHandler(clientSocket);
                clients.add(clientHandler);
                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void broadcast(String message, ClientHandler sender) {
        for (ClientHandler client : clients) {
            if (client != sender) {
                client.sendMessage(message);
            }
        }
    }

    private static class ClientHandler implements Runnable {
        private Socket clientSocket;
        private PrintWriter out;
        private BufferedReader in;
        private String username;

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
            try {
                out = new PrintWriter(clientSocket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            try {
                // Authentication
                boolean authenticated = false;
                while (!authenticated) {
                    out.println("Login or Register? (L/R)");
                    String choice = in.readLine().trim().toUpperCase();
                    
                    if (choice.equals("R")) {
                        if (handleRegistration()) authenticated = true;
                    } else if (choice.equals("L")) {
                        if (handleLogin()) authenticated = true;
                    } else {
                        out.println("Invalid choice. Please enter L or R.");
                    }
                }

                // Get username
                out.println("Enter your username:");
                this.username = in.readLine().trim();
                System.out.println("User " + username + " connected.");
                broadcast("User " + username + " joined the chat!", this);
                out.println("Welcome to the chat, " + username + "!");

                // Chat loop
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    broadcast("[" + username + "]: " + inputLine, this);
                }

                // Cleanup
                clients.remove(this);
                broadcast("User " + username + " left the chat.", this);
                System.out.println("User " + username + " disconnected.");
                
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    in.close();
                    out.close();
                    clientSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private boolean handleRegistration() throws IOException {
            out.println("Enter your email:");
            String email = in.readLine().trim();
            
            if (userExists(email)) {
                out.println("Email already registered. Please login.");
                return false;
            }
            
            out.println("Enter your password:");
            String password = in.readLine().trim();
            registerUser(email, password);
            out.println("Registration successful!");
            return true;
        }

        private boolean handleLogin() throws IOException {
            out.println("Enter your email:");
            String email = in.readLine().trim();
            
            out.println("Enter your password:");
            String password = in.readLine().trim();
            
            if (!checkCredentials(email, password)) {
                out.println("Invalid email or password.");
                return false;
            }
            out.println("Login successful!");
            return true;
        }

        private boolean userExists(String email) {
            try (BufferedReader reader = new BufferedReader(new FileReader("users.txt"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith(email + ":")) return true;
                }
            } catch (IOException e) {
                // File not found = no users yet
            }
            return false;
        }

        private boolean checkCredentials(String email, String password) {
            try (BufferedReader reader = new BufferedReader(new FileReader("users.txt"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.equals(email + ":" + password)) return true;
                }
            } catch (IOException e) {
                return false;
            }
            return false;
        }

        private void registerUser(String email, String password) {
            try (FileWriter fw = new FileWriter("users.txt", true);
                 BufferedWriter bw = new BufferedWriter(fw);
                 PrintWriter pw = new PrintWriter(bw)) {
                pw.println(email + ":" + password);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void sendMessage(String message) {
            out.println(message);
        }
    }
}