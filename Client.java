import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.List;

public class Client {
    private static final int MAX_SEQUENCE_NUMBER = 65536;
    private static final int INITIAL_WINDOW_SIZE = 1;
    private static final int MAX_WINDOW_SIZE = 65536;
    private static final int TIMEOUT = 1000; // Milliseconds

    private DatagramSocket clientSocket;
    private InetAddress serverAddress;
    private int serverPort;
    private boolean isConnected;
    private int windowSize;

    private List<Integer> windowSizeHistory = new ArrayList<>();
    private List<Integer> sentSeqNumHistory = new ArrayList<>();

    public Client(String serverIP, int serverPort) throws SocketException, UnknownHostException {
        clientSocket = new DatagramSocket();
        serverAddress = InetAddress.getByName(serverIP);
        this.serverPort = serverPort;
        isConnected = false;
        windowSize = INITIAL_WINDOW_SIZE;
    }

    public void start() {
        if (!isConnected) {
            // Send the initial string to the server
            String initialString = "network";
            try {
                sendData(initialString);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }

        byte[] receiveData = new byte[1024];
        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);

        // Receive connection setup success message from the server
        try {
            clientSocket.receive(receivePacket);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        String receivedData = new String(receivePacket.getData(), 0, receivePacket.getLength()).trim();

        if (receivedData.equals("Connection setup success")) {
            System.out.println("Connection established with server: " + serverAddress + ":" + serverPort);
            isConnected = true;

            // Start sending data segments
            try {
                sendDataSegments();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            System.out.println("Failed to establish a connection with the server.");
        }

        // Close the client socket after sending segments
        clientSocket.close();
    }

    private void sendDataSegments() throws IOException {
        int sequenceNumber = 0;
        int sentSegments = 0;
        int receivedAcks = 0;
        int lastAckSeqNum = -1;

        while (sentSegments < 10000000 && isConnected) {
            String segment = String.valueOf(sequenceNumber);
            sendData(segment);

            // Start a timer for each segment sent
            long startTime = System.currentTimeMillis();

            while (true) {
                // Check if an ACK has been received
                byte[] receiveData = new byte[1024];
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                clientSocket.setSoTimeout(TIMEOUT);

                try {
                    clientSocket.receive(receivePacket);
                    String receivedAckData = new String(receivePacket.getData(), 0, receivePacket.getLength()).trim();

                    // Split the receivedAckData by whitespaces to handle any potential leading/trailing spaces
                    String[] dataParts = receivedAckData.split("\\s+");
                    if (dataParts[0].equals("ACK")) {
                        int ackSeqNum = Integer.parseInt(dataParts[1]);
                        if (ackSeqNum > lastAckSeqNum) {
                            receivedAcks += ackSeqNum - lastAckSeqNum;
                            lastAckSeqNum = ackSeqNum;

                            // Adjust sliding window size based on ACK received
                            if (windowSize < MAX_WINDOW_SIZE) {
                                // Additive Increase
                                windowSize = Math.min(windowSize * 2, MAX_WINDOW_SIZE);
                            } else {
                                // Window is already at maximum size, maintain it
                                windowSize = MAX_WINDOW_SIZE;
                            }
                        }
                    }
                } catch (SocketTimeoutException e) {
                    // Timeout reached, no ACK received, assume segment loss
                    System.out.println("Timeout. Resending unacknowledged segment: " + sequenceNumber);
                    break;
                }

                // If all the segments are acknowledged, increase the window size
                if (receivedAcks == windowSize) {
                    receivedAcks = 0;
                }

                // If the window is full, stop the timer and move to the next segment
                if (sequenceNumber - lastAckSeqNum + 1 >= windowSize) {
                    break;
                }

                // If the timer exceeds the timeout, resend the unacknowledged segment
                if (System.currentTimeMillis() - startTime >= TIMEOUT) {
                    System.out.println("Timeout. Resending unacknowledged segment: " + sequenceNumber);
                    break;
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

            sequenceNumber++;

            // Slow down the ticks for sending segments
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void sendData(String message) throws IOException {
        byte[] sendData = message.getBytes();
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
