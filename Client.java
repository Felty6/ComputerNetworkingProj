import java.io.IOException;
import java.net.*;

public class Client {
	private static final int MAX_SEQUENCE_NUMBER = 65536; // Maximum sequence number
	private static final int INITIAL_WINDOW_SIZE = 1; // Sliding window initial
	private static final int MAX_WINDOW_SIZE = 65536; // Sliding window final

	private DatagramSocket clientSocket;
	private InetAddress serverAddress;
	private int serverPort;
	
	// constructor method for Client 
	public Client(String serverIP, int serverPort) throws SocketException, UnknownHostException {
		clientSocket = new DatagramSocket();                  // allow communication 
		serverAddress = InetAddress.getByName(serverIP);      // retrieves the IP address of server
		this.serverPort = serverPort;                       
	}

	public void start() {
		try {
			// Send the initial string to the server
			byte[] sendData = "network".getBytes();           
			DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, serverPort);
			clientSocket.send(sendPacket);

			// create new array with size of 1024 bytes
			byte[] receiveData = new byte[1024];
			DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
			// receive UDP packet sent by the server 
			clientSocket.receive(receivePacket);         
			
			// converting the received byte data to string 
			String receivedData = new String(receivePacket.getData(), 0, receivePacket.getLength());
			
			if (receivedData.equals("Connection setup success")) {
				System.out.println("Connection established with server: " + serverAddress + ":" + serverPort);

				// Start sending data segments
				sendDataSegments();
			}
	
		  // exception catch system 
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			clientSocket.close();
		}
	}
	
	// responsible for sending data segments to the server
	private void sendDataSegments() throws IOException {
		int sequenceNumber = 0;
		int sentSegments = 0;
		int receivedAcks = 0;
		int windowSize = INITIAL_WINDOW_SIZE;
		int totalSegments = 10000; // Total segments to be sent

		while (sentSegments < totalSegments) {
			// Send the next segment
			String segment = String.valueOf(sequenceNumber);
			byte[] sendData = segment.getBytes();
			DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, serverPort);
			clientSocket.send(sendPacket);

			// Increment sequence number and sentSegments
			sequenceNumber++;
			sentSegments++;

			// Receive ACK for the sent segment
			byte[] receiveData = new byte[1024];
			DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
			clientSocket.receive(receivePacket);
			String receivedData = new String(receivePacket.getData(), 0, receivePacket.getLength());

			// Split the receivedData by whitespaces to handle any potential leading/trailing spaces
			String[] dataParts = receivedData.split("\\s+");
			if (dataParts[0].equals("ACK")) {
				int ackSeqNum = Integer.parseInt(dataParts[1]);
				if (ackSeqNum == sequenceNumber) {
					receivedAcks++;
				}
			}

			// Sliding window adjustment
			if (sentSegments % 1024 == 0 || receivedAcks == windowSize) {
				if (windowSize < MAX_WINDOW_SIZE) {
					windowSize *= 2;
				}

				if (receivedAcks == windowSize) {
					receivedAcks = 0;
				}
			}

			// Output the current status periodically
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
