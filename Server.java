import java.io.*;
import java.net.*;

public class Server {
    private static final int MAX_SEQUENCE_NUMBER = 65536; // 2^16, maximum sequence number
    private static final int INITIAL_WINDOW_SIZE = 1;
    private static final int MAX_WINDOW_SIZE = 216;

    private DatagramSocket serverSocket;
    private InetAddress clientAddress;
    private int clientPort;
    private int expectedSeqNum;
    private int windowSize;

    public Server(int port) throws SocketException {
        serverSocket = new DatagramSocket(port);
        expectedSeqNum = 0;
        windowSize = INITIAL_WINDOW_SIZE;
    }

    public void start() throws IOException {
        byte[] receiveData = new byte[1024];
        byte[] sendData;

        System.out.println("Server is running and waiting for a connection...");

        while (true) {
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            serverSocket.receive(receivePacket);
            String receivedData = new String(receivePacket.getData()).trim();

            if (receivedData.equals("network")) {
                // Connection setup success message
                clientAddress = receivePacket.getAddress();
                clientPort = receivePacket.getPort();
                String response = "Connection setup success";
                sendData = response.getBytes();
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, clientAddress, clientPort);
                serverSocket.send(sendPacket);

                System.out.println("Connection established with client: " + clientAddress + ":" + clientPort);
                break;
            }
        }

        // Start receiving data segments from the client
        receiveDataSegments();
    }

    private void receiveDataSegments() throws IOException {
        int sentSegments = 0;
        int receivedSegments = 0;
        int missingSegments = 0;

        while (receivedSegments < 10000000) {
            byte[] receiveData = new byte[1024];
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            serverSocket.receive(receivePacket);
            int receivedSeqNum = Integer.parseInt(new String(receivePacket.getData()).trim());

            if (receivedSeqNum == expectedSeqNum) {
                // Correct segment received, send ACK
                String ackMsg = "ACK: " + (receivedSeqNum + 1);
                byte[] sendData = ackMsg.getBytes();
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, clientAddress, clientPort);
                serverSocket.send(sendPacket);

                expectedSeqNum++;
                windowSize = Math.min(MAX_WINDOW_SIZE, windowSize * 2);

                receivedSegments++;
            } else {
                // Incorrect segment received, resend ACK for the last expected segment
                String ackMsg = "ACK: " + expectedSeqNum;
                byte[] sendData = ackMsg.getBytes();
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, clientAddress, clientPort);
                serverSocket.send(sendPacket);

                windowSize = Math.max(1, windowSize / 2);

                missingSegments++;
            }

            sentSegments++;

            // Periodically report average good-put
            if (sentSegments % 1000 == 0) {
                double goodPut = (double) receivedSegments / sentSegments;
                System.out.println("Sent segments: " + sentSegments +
                                   ", Received segments: " + receivedSegments +
                                   ", Missing segments: " + missingSegments +
                                   ", Window size: " + windowSize +
                                   ", Good-put: " + goodPut);
            }
        }

        double finalGoodPut = (double) receivedSegments / sentSegments;
        System.out.println("Final Sent segments: " + sentSegments +
                           ", Received segments: " + receivedSegments +
                           ", Missing segments: " + missingSegments +
                           ", Window size: " + windowSize +
                           ", Final Good-put: " + finalGoodPut);
    }

    public static void main(String[] args) {
        int serverPort = 12345; // Change this to the desired server port
        try {
            Server server = new Server(serverPort);
            server.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
