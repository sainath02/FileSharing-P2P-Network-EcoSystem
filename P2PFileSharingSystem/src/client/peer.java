package client;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Handler;
import java.util.logging.Logger;

import server.Server;

public class peer {

	private static int serverPort = 8080;
	private static int clientPort = -1;
	private static int neighborPort = -1;
	private static String hostName = "localhost";
	private static ArrayList<Integer> chunksList = null;
	private static HashMap<String, Long> chunks = new HashMap<>();
	private static int chunkscount;
	private static String peerDir = "PeerDB" + serverPort;
	private static String serverFile = "serverFile.txt";
	static final Logger log = Logger.getLogger(peer.class.getName());

	public static void main(String[] args) {
		// String serverName = args[0];
		// serverPort = Integer.parseInt(args[0]);
		// clientPort = Integer.parseInt(args[1]);
		// neighborPort = Integer.parseInt(args[2]);
		serverPort = 8080;
		clientPort = 8000;
		neighborPort = 8080;

		log.info("Peer: " + hostName + ":" + clientPort + " Server: " + hostName + ":" + serverPort + " Neighbor: "
				+ hostName + ":" + neighborPort);

		// getFileChunksFromServer();
		new NeighbourConnect().start();

	}

	private static void getFileChunksFromServer() {
		File dir = new File(peerDir);
		dir.mkdir();

		try {
			System.out.println("Connecting to " + hostName + " on port " + serverPort);
			Socket client = new Socket(hostName, serverPort);
			System.out.println("Just connected to " + client.getRemoteSocketAddress());

			OutputStream outToServer = client.getOutputStream();
			InputStream inFromServer = client.getInputStream();

			DataOutputStream out = new DataOutputStream(outToServer);
			DataInputStream in = new DataInputStream(inFromServer);

			out.writeUTF("Hello from " + client.getLocalSocketAddress());
			System.out.println("Server says " + in.readInt());

			out.writeUTF("Count of Chunks");
			chunkscount = in.readInt();
			chunksList = new ArrayList<>();
			for (int i = 0; i < chunkscount; i++)
				chunksList.add(i);

			while (chunkscount > 0 && chunksList.size() >= 0) {
				String next = null;
				boolean flag = false;
				if (flag) {
					out.writeUTF("Get Next Chunk Name");
					out.flush();
					next = in.readUTF();
					flag = false;
				} else {
					next = getNextChunk();
					if (next == null) {
						// System.out.println("Inside if next == null");
						buildClientFile();
						return;
					}
				}
				System.out.println("Requesting file from server: " + next);
				out.writeUTF("Get File" + "#" + next);

				System.out.println("Completed 2nd request");

				File f = new File(dir, next);
				f.createNewFile();
				FileOutputStream fos = new FileOutputStream(f);
				byte[] bytes = new byte[server.Server.chunkSize];

				int count;
				int totalBytes = 0;
				int size = server.Server.chunkSize;
				while ((count = in.read(bytes)) >= 0) {
					totalBytes += count;
					System.out.println("Writing " + count + " total : " + totalBytes + " size : " + size);
					fos.write(bytes, 0, count);
					System.out.println("Total Bytes: " + totalBytes);
					System.out.println("chunkSize " + size);
					if (totalBytes == size)
						break;
				}
				fos.flush();
				System.out.println("File is created");
			}

			String chunkname = serverFile + ".1";

			System.out.println("Closing the connection to server...");
			out.writeUTF("close");
			client.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	private static String getNextChunk() {
		// System.out.println("Inside getNextChunk. chunksList size is: " +
		// chunksList.size());
		if (chunksList.size() > 0) {
			int randomId = ThreadLocalRandom.current().nextInt(0, chunksList.size());
			int nextChunkId = chunksList.get(randomId);
			chunksList.remove(randomId);
			return serverFile + "." + nextChunkId;
		}
		return null;
	}

	private static boolean alreadyExits = false;

	private static void buildClientFile() throws IOException {
		File dir = new File(peerDir);

		if (!dir.exists())
			dir.mkdir();
		if (alreadyExits)
			return;

		File f = new File(dir, serverFile);
		f.createNewFile();
		FileOutputStream fos = new FileOutputStream(f);

		for (int i = 0; i < chunkscount; i++) {
			String chunkname = serverFile + "." + i;
			System.out.println("Copying file data from: " + chunkname + " to file: " + serverFile);
			File chunk = new File(dir, chunkname);
			Files.copy(chunk.toPath(), fos);
		}
		fos.flush();
		System.out.println("Downloaded file successfully");
		fos.close();
		alreadyExits = true;
	}

	private static class NeighbourConnect extends Thread {
		public void run() {

			File dir = new File(peerDir);
			if (!dir.exists())
				dir.mkdir();

			Socket neighbor = null;
			boolean isConnected = false;

			while (!isConnected) {
				try {
					// System.out.println("Connecting to " + hostName +" on port
					// " + serverPort);
					neighbor = new Socket(hostName, neighborPort);
					isConnected = neighbor.isConnected();
					System.out.println("Connected to neighbour " + neighbor.getRemoteSocketAddress());
				} catch (Exception e) {
					if (neighbor == null || !neighbor.isConnected()) {
						log.info("Cloudn't connect to neighbour. Sleeping thread for 3 secs");
						try {
							Thread.sleep(3000);
						} catch (InterruptedException e1) {
							e1.printStackTrace();
						}
					}
				}
			}

			try {
				OutputStream outToServer = neighbor.getOutputStream();
				InputStream inFromServer = neighbor.getInputStream();

				DataOutputStream out = new DataOutputStream(outToServer);
				DataInputStream in = new DataInputStream(inFromServer);

				out.writeUTF("Hello from " + neighbor.getLocalSocketAddress());
				System.out.println("Neighbor says " + in.readInt());

				// Next four lines should be commented and handled in server
				// connection thread.
				out.writeUTF("Count of Chunks");
				chunkscount = in.readInt();
				chunksList = new ArrayList<>();
				for (int i = 0; i < chunkscount; i++)
					chunksList.add(i);

				while (chunkscount > 0 && chunksList.size() >= 0) {
					String next = null;
					boolean flag = false;
					next = getNextChunk();
					if (next == null) {
						buildClientFile();
						System.out.println("Closing the connection from neighbour...");
						out.writeUTF("close");
						return;
					}

					System.out.println("Requesting file from server: " + next);
					out.writeUTF("Get File" + "#" + next);

					System.out.println("Completed 2nd request");

					File f = new File(dir, next);
					f.createNewFile();
					FileOutputStream fos = new FileOutputStream(f);
					byte[] bytes = new byte[server.Server.chunkSize];

					int count;
					int totalBytes = 0;
					int size = server.Server.chunkSize;
					while ((count = in.read(bytes)) >= 0) {
						totalBytes += count;
						System.out.println("Writing " + count + " total : " + totalBytes + " size : " + size);
						fos.write(bytes, 0, count);
						System.out.println("Total Bytes: " + totalBytes);
						System.out.println("chunkSize " + size);
						if (totalBytes == size)
							break;
					}
					fos.flush();
					System.out.println("File is created");
				}

				System.out.println("Closing the connection to neighbour...");
				out.writeUTF("close");
				neighbor.close();
			} catch (IOException e) {
				e.printStackTrace();
			}

			// if (neighbor != null || !neighbor.isConnected()) {
			// try {
			// neighbor.close();
			// } catch (IOException e) {
			// e.printStackTrace();
			// }
			// }
		}
	}

	private static class ShareFile extends Thread {
		
		ServerSocket peerSocket = null;
		DataInputStream in;
		DataOutputStream out;
		
		public void run() {
			try {
				peerSocket = new ServerSocket(serverPort);
				System.out.println("Peer started sharing file");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			try {
				while (true) {
					Socket neighbor = peerSocket.accept();
					System.out.println("Listening to peer "+ neighbor.getRemoteSocketAddress());
					
					in = new DataInputStream(neighbor.getInputStream());
	    			out = new DataOutputStream(neighbor.getOutputStream());
	    			
	    			String request;
	    			while(true){
	    				try {
	    					request = in.readUTF();
	    					break;
	    				}catch(Exception e){
	    					//Exception ignored
	    				}
	    			}
	    				    			
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}
	}
}
