import java.io.IOException;
import java.net.*;

public class Client {
    private static final int MAX_SEQUENCE_NUMBER = 65536; // maximum sequence number
    private static final int INITIAL_WINDOW_SIZE = 1; // sliding window initial
    private static final int MAX_WINDOW_SIZE = 65536; // sliding window final

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
        int sentSegments = 0;
        int receivedAcks = 0;
        int windowSize = INITIAL_WINDOW_SIZE;

        while (sentSegments < 10000000) {
            int base = sentSegments;

            for (int i = 0; i < windowSize; i++) {
                int sequenceNumber = base + i;
                if (sequenceNumber >= 10000000) {
                    break; // Reached the end, stop sending
                }

                String segment = String.valueOf(sequenceNumber);
                byte[] sendData = segment.getBytes();
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, serverPort);
                clientSocket.send(sendPacket);

                sentSegments++;
            }

            for (int i = 0; i < windowSize; i++) {
                byte[] receiveData = new byte[1024];
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                clientSocket.receive(receivePacket);

                String receivedData = new String(receivePacket.getData()).trim();
                String[] dataParts = receivedData.split("\\s+");

                if (dataParts[0].equals("ACK")) {
                    int ackSeqNum = Integer.parseInt(dataParts[1]);
                    if (ackSeqNum > base) {
                        int ackedSegments = ackSeqNum - base;
                        receivedAcks += ackedSegments;
                        break;
                    }
                }
            }

            if (sentSegments % 1024 == 0 || receivedAcks == windowSize) {
                if (windowSize < MAX_WINDOW_SIZE) {
                    windowSize *= 2;
                }

                if (receivedAcks == windowSize) {
                    receivedAcks = 0;
                }
            }

            if (sentSegments % 1000 == 0) {
                double goodPut = (double) sentSegments / (sentSegments - receivedAcks);
                System.out.println("Sent segments: " + sentSegments + ", Received ACKs: " + receivedAcks
                        + ", Window size: " + windowSize + ", Good-put: " + goodPut);
            }
        }
    }

    public static void main(String[] args) {
        String serverIP = "192.168.1.123"; // IP address of the server computer
        int serverPort = 6463; // Input server's port

        try {
            Client client = new Client(serverIP, serverPort);
            client.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
