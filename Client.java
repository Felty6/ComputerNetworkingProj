import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

public class Client {
    private static final int MAX_SEQUENCE_NUMBER = 65536;
    private static final int INITIAL_WINDOW_SIZE = 1;
    private static final int MAX_WINDOW_SIZE = 216;

    private DatagramSocket clientSocket;
    private InetAddress serverAddress;
    private int serverPort;

    private List<Integer> windowSizeHistory = new ArrayList<>();
    private List<Integer> sentSeqNumHistory = new ArrayList<>();

    public Client(String serverIP, int serverPort) throws SocketException, UnknownHostException {
        clientSocket = new DatagramSocket();
        serverAddress = InetAddress.getByName(serverIP);
        this.serverPort = serverPort;
    }

    public void start() throws IOException {
        // Ping the server IP address to check if the server is running
        if (!pingServer()) {
            System.out.println("Unsuccessful connection. Server of IP address given is not running.");
            return;
        }

        // Establish a connection with the server
        byte[] sendData = "network".getBytes();
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, serverPort);
        clientSocket.send(sendPacket);

        byte[] receiveData = new byte[1024];
        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
        clientSocket.receive(receivePacket);
        String receivedData = new String(receivePacket.getData()).trim();

        if (receivedData.equals("Connection setup success")) {
            System.out.println("Connection established with server: " + serverAddress + ":" + serverPort);
            sendDataSegments();
        }

        // Gracefully disconnect when data segments are sent
        sendDisconnectionMessage();
        clientSocket.close();
    }

    private boolean pingServer() {
        try {
            InetAddress serverAddr = InetAddress.getByName("192.168.1.123");
            if (serverAddr.isReachable(5000)) {
                return true;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    private void sendDataSegments() throws IOException {
        int sequenceNumber = 0;
        int sentSegments = 0;
        int receivedAcks = 0;
        int windowSize = INITIAL_WINDOW_SIZE;

        while (sentSegments < 10000000) {
            // Simulate segment loss by not sending every 1024th segment
            if (sentSegments % 1024 == 0) {
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

            // Store the window size
            windowSizeHistory.add(windowSize);
            if (sentSegments % 1000 == 0) {
                // Goodput formula
                double goodPut = (double) sentSegments / (sentSegments - receivedAcks);
                System.out.println("Sent segments: " + sentSegments + ", Received ACKs: " + receivedAcks
                        + ", Window size: " + windowSize + ", Good-put: " + goodPut);
            }
        }
    }

    private void sendDisconnectionMessage() throws IOException {
        // Send a message to indicate that the client is disconnecting
        String disconnectMsg = "Client disconnecting";
        byte[] sendData = disconnectMsg.getBytes();
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, serverPort);
        clientSocket.send(sendPacket);
    }

    public static void main(String[] args) {
        String serverIP = "192.168.1.123";
        int serverPort = 6463;

        try {
            Client client = new Client(serverIP, serverPort);
            client.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
