/*
 * CS 158A Project Client Side 
 * Name: Rio Taiga 
 */

package ComputerNetworkingProj;

import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.List;

public class Client {
	// Constants for protocol parameters
	private static final int MAX_SEQUENCE_NUMBER = 65536;
	private static final int INITIAL_WINDOW_SIZE = 1;
	private static final int MAX_WINDOW_SIZE = 65536;
	private static final int TIMEOUT = 1000; // Milliseconds (1 second)

	// Socket and connection variables
	private DatagramSocket clientSocket;
	private InetAddress serverAddress;
	private int serverPort;
	private boolean isConnected;
	private int windowSize;

	// Lists to store history data
	private List<Integer> windowSizeHistory = new ArrayList<>();
	private List<Integer> sentSeqNumHistory = new ArrayList<>();

	public Client(String serverIP, int serverPort) throws SocketException, UnknownHostException {
		// Create a new DatagramSocket for the client
		clientSocket = new DatagramSocket();

		// Store the server's address and port
		serverAddress = InetAddress.getByName(serverIP);
		this.serverPort = serverPort;

		// Set initial values for the client's sliding window
		isConnected = false;
		windowSize = INITIAL_WINDOW_SIZE;
	}

	public void start() {
		if (!isConnected) {
			// Send the initial string to the server
			String initialString = "network";

			// Receive connection setup success message from the server
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
			// Connection successfully established with the server
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

		// Initialize the client's window size to the server's initial window size
		windowSize = INITIAL_WINDOW_SIZE;

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

					// Split the receivedAckData by whitespaces to handle any potential
					// leading/trailing spaces
					String[] dataParts = receivedAckData.split("\\s+");
					if (dataParts[0].equals("ACK")) {
						int ackSeqNum = Integer.parseInt(dataParts[1]);
						if (ackSeqNum > lastAckSeqNum) {

							// Received a new ACK, update sliding window size
							receivedAcks += ackSeqNum - lastAckSeqNum;
							lastAckSeqNum = ackSeqNum;

							// Update sliding window size based on ACK received
							windowSize = Integer.parseInt(dataParts[2]);
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
		}
	}

	private void sendData(String message) throws IOException {
		
        // Convert the message to bytes and create a DatagramPacket to send to the server
		byte[] sendData = message.getBytes();
		DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, serverPort);
		clientSocket.send(sendPacket);
	}

	public static void main(String[] args) {
		
        // Server IP and port that will be connecting to
		String serverIP = "192.168.1.123";
		int serverPort = 5000;

		try {
            // Create and start the client
			Client client = new Client(serverIP, serverPort);
			client.start();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
