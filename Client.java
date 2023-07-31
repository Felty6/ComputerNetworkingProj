package ComputerNetworkingProj;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

public class Client {
    private static final int MAX_SEQUENCE_NUMBER = 65536;
    private static final int INITIAL_WINDOW_SIZE = 1;
    private static final int MAX_WINDOW_SIZE = 216;

    private DatagramSocket clientSocket;
    private InetAddress serverAddress;
    private int serverPort;

    public Client(String serverIP, int serverPort) throws SocketException, UnknownHostException {
        clientSocket = new DatagramSocket();
        serverAddress = InetAddress.getByName(serverIP);
        this.serverPort = serverPort;
    }

    public void start() throws IOException {
        // Send the initial string to the server
        byte[] sendData = "network".getBytes();
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, serverPort);
        clientSocket.send(sendPacket);

        byte[] receiveData = new byte[1024];
        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
        clientSocket.receive(receivePacket);
        String receivedData = new String(receivePacket.getData()).trim();

        if (receivedData.equals("Connection setup success")) {
            System.out.println("Connection established with server: " + serverAddress + ":" + serverPort);

            // Start sending data segments
            sendDataSegments();
        }

        clientSocket.close();
    }

    private void sendDataSegments() throws IOException {
        int sequenceNumber = 0;
        int sentSegments = 0;
        int receivedAcks = 0;
        int windowSize = INITIAL_WINDOW_SIZE;

        while (sentSegments < 10000000) {
            if (sentSegments % 1024 == 0) {
                // Simulate segment loss by not sending every 1024th segment
                if (Math.random() < 0.2) {
                    System.out.println("Segment loss: " + sequenceNumber);
                    sequenceNumber++;
                    continue;
                }
            }

            String segment = String.valueOf(sequenceNumber);
            byte[] sendData = segment.getBytes();
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, serverPort);
            clientSocket.send(sendPacket);

            byte[] receiveData = new byte[1024];
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            clientSocket.receive(receivePacket);
            String receivedData = new String(receivePacket.getData()).trim();

            // Split the receivedData by whitespaces to handle any potential leading/trailing spaces
            String[] dataParts = receivedData.split("\\s+");
            if (dataParts[0].equals("ACK")) {
                int ackSeqNum = Integer.parseInt(dataParts[1]);
                if (ackSeqNum == sequenceNumber + 1) {
                    sequenceNumber++;
                    receivedAcks++;
                }
            }

            if (sentSegments % 1024 == 0 || receivedAcks == windowSize) {
                // Sliding window adjustment
                if (windowSize < MAX_WINDOW_SIZE) {
                    windowSize *= 2;
                }

                if (receivedAcks == windowSize) {
                    receivedAcks = 0;
                }
            }

            sentSegments++;

            if (sentSegments % 1000 == 0) {
                double goodPut = (double) sentSegments / (sentSegments - receivedAcks);
                System.out.println("Sent segments: " + sentSegments + ", Received ACKs: " + receivedAcks
                        + ", Window size: " + windowSize + ", Good-put: " + goodPut);
            }
        }
    }

    public static void main(String[] args) {
        String serverIP = "YOUR_SERVER_IP"; // IP address of the server computer
        int serverPort = 6463; // Input server's port

        try {
            Client client = new Client(serverIP, serverPort);
            client.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
}
