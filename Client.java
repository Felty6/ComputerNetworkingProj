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
    private boolean isConnected = false;
    private int nextExpectedSeqNum = 0;
    private int windowSize = INITIAL_WINDOW_SIZE;
    private List<Integer> windowSizeHistory = new ArrayList<>();
    private List<Integer> sentSeqNumHistory = new ArrayList<>();

    public Client(String serverIP, int serverPort) throws SocketException, UnknownHostException {
        clientSocket = new DatagramSocket();
        serverAddress = InetAddress.getByName(serverIP);
        this.serverPort = serverPort;
    }

    public void start() throws IOException {
        if (!isConnected) {
            // Send the initial string to the server
            String initialString = "network";
            sendData(initialString.getBytes());
        }

        byte[] receiveData = new byte[1024];
        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
        clientSocket.setSoTimeout(TIMEOUT);

        try {
            clientSocket.receive(receivePacket);
        } catch (SocketTimeoutException e) {
            System.out.println("Timeout. Unable to connect to the server.");
            clientSocket.close();
            return;
        }

        String receivedData = new String(receivePacket.getData()).trim();

        if (receivedData.equals("Connection setup success")) {
            System.out.println("Connection established with server: " + serverAddress + ":" + serverPort);
            isConnected = true;

            // Start sending data segments
            sendDataSegments();
        } else {
            System.out.println("Connection setup failed.");
            clientSocket.close();
        }
    }

    private void sendDataSegments() throws IOException {
        int sequenceNumber = 0;
        int sentSegments = 0;
        int receivedAcks = 0;
        int lastAckSeqNum = 0;

        while (sentSegments < 10000000 && isConnected) {
            int[] segment = new int[1024];

            // Prepare the segment with 1024 sequence numbers
            for (int i = 0; i < 1024; i++) {
                segment[i] = sequenceNumber;
                sequenceNumber++;
            }

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
                    String receivedData = new String(receivePacket.getData()).trim();

                    // Split the receivedData by whitespaces to handle any potential leading/trailing spaces
                    String[] dataParts = receivedData.split("\\s+");
                    if (dataParts[0].equals("ACK")) {
                        int ackSeqNum = Integer.parseInt(dataParts[1]);
                        if (ackSeqNum > lastAckSeqNum) {
                            receivedAcks += ackSeqNum - lastAckSeqNum;
                            lastAckSeqNum = ackSeqNum;
                        }
                    }
                } catch (SocketTimeoutException e) {
                    // Resend the unacknowledged segment
                    System.out.println("Timeout. Resending unacknowledged segments.");
                    break;
                }

                // If all the segments are acknowledged, increase the window size
                if (receivedAcks == windowSize) {
                    windowSize = Math.min(MAX_WINDOW_SIZE, windowSize * 2);
                    receivedAcks = 0;
                }

                // If the window is full, stop the timer and move to the next segment
                if (sequenceNumber - lastAckSeqNum >= windowSize) {
                    break;
                }

                // If the timer exceeds the timeout, resend the unacknowledged segment
                if (System.currentTimeMillis() - startTime >= TIMEOUT) {
                    System.out.println("Timeout. Resending unacknowledged segments.");
                    break;
                }
            }

            sentSegments++;

            // Store the window size
            windowSizeHistory.add(windowSize);
            if (sentSegments % 1000 == 0) {
                // Goodput formula
                double goodPut = (double) receivedAcks / sentSegments;
                System.out.println("Sent segments: " + sentSegments + ", Received ACKs: " + receivedAcks
                        + ", Window size: " + windowSize + ", Good-put: " + goodPut);
            }
        }

        isConnected = false;
        sentSeqNumHistory.clear();
        clientSocket.close();
    }

    private void sendData(int[] segment) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int seqNum : segment) {
            sb.append(seqNum).append(" ");
        }
        byte[] sendData = sb.toString().getBytes();
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
