package client;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.logging.Handler;
import java.util.logging.Logger;

public class Peer {

	static {
		// Program log configuration
		System.setProperty("java.util.logging.SimpleFormatter.format",
				"%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS.%1$tL %4$-7s (%2$s) %5$s %6$s%n");
	}

	private static int serverPort = 8080;
	private static int clientPort = -1;
	private static int neighborPort = -1;
	private static String hostName = "localhost";
	private static ArrayList<Integer> chunksList = null;
	private static HashMap<String, Long> chunks = new HashMap<>();
	private static int chunkscount = 10;
	private static int noOfPeers;
	private static int peerId;
	private static String peerDir;
	private static String serverFile = "serverFile.txt";
	private static int fileSize = 1000 * 1024;// Default size is 1000KB
	public static final int chunkSize = 100 * 1024; // Each chunk having 100KB
	public static String infoFile = "InformationFile";

	static final Logger log = Logger.getLogger(Peer.class.getName());

	public static void main(String[] args) throws IOException {
		// String serverName = args[0];

		System.out.println("Hello Client. Setting your PEER configurations and downloading your file from PEER system");

		Properties properties = new Properties();
		System.out.println();

		// Taking peer properties from configuration file.
		FileInputStream fs = new FileInputStream("src/server/config.properties");
		properties.load(fs);

		if (properties.isEmpty() || args.length < 1) {
			System.err.println("No client properties are found.");
			System.err.println("Please provide peer number in Arguments and rerun the system");
			log.info("Existing the system");
			System.exit(0);
		}

		String serverProperties = properties.getProperty(args[0]);
		String[] propArray = serverProperties.split(",");

		if (propArray.length != 2) {
			System.err.println(
					"Provide server address, peer address, neighbour address in config.properties and rerun the system");
			log.info("Existing the system");
			System.exit(0);
		}

		serverPort = Integer.parseInt(properties.getProperty("0").split(",")[0]);
		clientPort = Integer.parseInt(propArray[0]);
		neighborPort = Integer.parseInt(properties.getProperty(propArray[1]).split(",")[0]);

		peerDir = "PeerDB" + args[0];

		log.info("Peer: " + hostName + ":" + clientPort + " Server: " + hostName + ":" + serverPort + " Neighbor: "
				+ hostName + ":" + neighborPort);

		getFileChunksFromServer();

		new NeighbourConnection().start();

		new ShareFile().start();

	}

	private static void getFileChunksFromServer() {

		File dir = new File(peerDir);
		if (dir.exists())
			deleteDirectory(dir);
		dir.mkdir();

		try {
			log.info("Connecting to " + hostName + ":" + serverPort);
			Socket client = new Socket(hostName, serverPort);
			log.info("Just connected to " + client.getRemoteSocketAddress());

			OutputStream outToServer = client.getOutputStream();
			InputStream inFromServer = client.getInputStream();

			DataOutputStream out = new DataOutputStream(outToServer);
			DataInputStream in = new DataInputStream(inFromServer);

			out.writeUTF("Hello from " + client.getLocalSocketAddress());
			out.flush();

			String peerInfoStr = (String) in.readUTF();
			System.out.println("Server info: " + peerInfoStr);

			String[] peerInfo = peerInfoStr.split("#");
			int peerId = Integer.parseInt(peerInfo[0]);
			serverFile = peerInfo[1];
			chunkscount = Integer.parseInt(peerInfo[2].trim());
			noOfPeers = Integer.parseInt(peerInfo[3].trim());

			chunksList = new ArrayList<>();
			for (int i = 0; i < chunkscount; i++)
				chunksList.add(i);

			System.out.println(
					"PeerId is " + peerId + " serverFile is " + serverFile + " chunks count is " + chunkscount);

			int status = 0;
			int nextChunk = peerId;
			while (nextChunk < chunkscount) {
				String chunkName = serverFile + "." + nextChunk;

				out.writeUTF("Get details of chunk" + "#" + chunkName);
				out.flush();
				String chunkInformation = (String) in.readUTF();
				String[] chunkInfo = chunkInformation.split("#");
				int size = Integer.parseInt(chunkInfo[2]);
				out.writeUTF("Get File" + "#" + chunkName);
				out.flush();

				File f = new File(dir, chunkName);
				f.createNewFile();
				FileOutputStream fos = new FileOutputStream(f);
				// Harded code chunkSize value. Request from server if dynamic
				// chunkSizes are used.
				byte[] bytes = new byte[chunkSize];

				int count;
				int totalBytes = 0;
				if (size < 0)
					size = chunkSize;
				while ((count = in.read(bytes)) >= 0) {
					totalBytes += count;
					// System.out.println("Writing " + count + " total : " +
					// totalBytes + " size : " + size);
					fos.write(bytes, 0, count);
					// System.out.println("Total Bytes: " + totalBytes);
					// System.out.println("chunkSize " + size);
					if (totalBytes == size)
						break;
				}
				fos.flush();

				updateChunksInfo(chunkName);
				System.out.println("Chunk '" + chunkName + "' is downloaded");

				status++;
				nextChunk = peerId + status * noOfPeers;
			}
			if (chunks.size() == chunkscount) {
				buildClientFile();
				log.info("No files to get from peers. Hence existing the system");
				System.exit(0);
			}

			System.out.println("Closing the connection to server...");
			out.writeUTF("close");
			client.close();

		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	private static void deleteDirectory(File dir) {

		String[] entries = dir.list();
		for (String s : entries) {
			File currentFile = new File(dir.getPath(), s);
			currentFile.delete();
		}
		dir.delete();

	}

	private static String getNextChunk() {

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
		System.out.println("Downloaded complete file successfully");
		fos.close();
		alreadyExits = true;

	}

	private static class NeighbourConnection extends Thread {
		public void run() {

			File dir = new File(peerDir);
			if (!dir.exists())
				dir.mkdir();

			Socket neighbor = null;
			boolean isConnected = false;

			while (!isConnected) {

				try {
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

			int neighborRequestAttempts = 0;
			try {
				OutputStream outToServer = neighbor.getOutputStream();
				InputStream inFromServer = neighbor.getInputStream();

				DataOutputStream out = new DataOutputStream(outToServer);
				DataInputStream in = new DataInputStream(inFromServer);
				ObjectInputStream inObj = new ObjectInputStream(inFromServer);

				while (true) {
					out.writeUTF("Get Chunks Information");
					HashMap<String, Long> neighborChunkList = (HashMap<String, Long>) inObj.readObject();

					HashMap<String, Long> requiredList = getRequiredList(neighborChunkList);

					if (requiredList.size() == 0) {
						if (neighborRequestAttempts < 5) {
							neighborRequestAttempts++;
							Thread.sleep(1000);
							continue;
						}
					}

					neighborRequestAttempts = 0;
					// log.info("Neighbour has below " + requiredList.size() + "
					// different chunks shared");
					// for(String s : neighborChunkList.keySet()){
					// System.out.print(s +" ");
					// }
					// System.out.println();

					for (String next : requiredList.keySet()) {

						log.info("Requesting file from Neighbour: " + next);

						out.writeUTF("Get details of chunk" + "#" + next);
						out.flush();
						String chunkInformation = (String) in.readUTF();
						String[] chunkInfo = chunkInformation.split("#");
						int size = Integer.parseInt(chunkInfo[2]);

						out.writeUTF("Get File" + "#" + next);
						out.flush();

						File f = new File(dir, next);
						if (f.exists())
							continue;

						f.createNewFile();
						FileOutputStream fos = new FileOutputStream(f);
						byte[] bytes = new byte[chunkSize];

						int count;
						int totalBytes = 0;

						while ((count = in.read(bytes)) >= 0) {
							totalBytes += count;
							// System.out.println("Writing " + count + " total :
							// " + totalBytes + " size : " + size);
							fos.write(bytes, 0, count);
							// System.out.println("Total Bytes: " + totalBytes);
							// System.out.println("chunkSize " + size);
							if (totalBytes >= size) {
								// System.out.println("Copied complete file");
								break;
							}
						}
						fos.flush();
						updateChunksInfo(next);

						log.info("Downloaded '" + next + "' from Neighbour");
						// log.info("Current residing files are");
						// for(String s : chunks.keySet()){
						// System.out.println(s);
						// }

					}

					if (chunks.size() == chunkscount) {
						buildClientFile();
						break;
					}
				}

			} catch (IOException e) {
				e.printStackTrace();
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
			} finally {
				if (neighbor != null && neighbor.isConnected()) {
					try {
						neighbor.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}

			if (chunks.size() == chunkscount) {
				try {
					buildClientFile();

					return;
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

		}
	}

	private static class ShareFile extends Thread {

		ServerSocket peerSocket = null;
		DataInputStream in;
		DataOutputStream out;
		ObjectOutputStream obj;

		public void run() {
			try {
				peerSocket = new ServerSocket(clientPort);
				System.out.println("Peer started sharing file");
			} catch (IOException e) {
				e.printStackTrace();
			}

			try {
				while (true) {
					Socket neighbor = peerSocket.accept();
					System.out.println("Listening to peer " + neighbor.getRemoteSocketAddress());

					in = new DataInputStream(neighbor.getInputStream());
					out = new DataOutputStream(neighbor.getOutputStream());
					obj = new ObjectOutputStream(neighbor.getOutputStream());

					String request;
					while (true) {
						try {
							request = in.readUTF();

							if (request.startsWith("Get Chunks Information")) {
								log.info("Sending current Chunks Information to neighbour " + neighbor.toString());
								obj.writeObject(existingChunksAsMap());
							}

							else if (request.startsWith("Get details of chunk")) {
								// log.info("Request Message is: "+request);
								String filename = request.split("#")[1].trim();
								File f = new File(peerDir, filename);
								String response = peerId + "#" + filename + "#" + f.length();
								out.writeUTF(response);
								out.flush();
							}

							else if (request.startsWith("Get File")) {
								// log.info("Request Message is: " + request);
								String filename = request.split("#")[1].trim();
								log.info("Uploading chunk with name '" + filename + "' to peer");

								File f = new File(peerDir, filename);
								int count;
								byte[] buffer = new byte[chunkSize];
								OutputStream o = neighbor.getOutputStream();

								FileInputStream i = new FileInputStream(f);

								while ((count = i.read(buffer)) > 0) {
									o.write(buffer, 0, count);
									o.flush();
								}
								log.info("Uploaded chunk to peer");

							}

							else if (request.startsWith("close")) {
								log.info("Peer Neighbour " + neighbor.getRemoteSocketAddress()
										+ " has requested to close connection");
								System.out.println("Closing connection");
								neighbor.close();
								break;
							}

							else if (request.startsWith("Count of Chunks")) {
								// System.out.println("Request Message is: " +
								// request);
								out.writeInt(10);
								out.flush();
							}

						} catch (Exception e) {
							// Exception ignored
						}
					}

				}
			} catch (IOException e) {
				e.printStackTrace();
			}

		}
	}

	private synchronized static void updateChunksInfo(String chunkName) {
		chunks.put(chunkName, (long) 100);
	}

	private synchronized static HashMap<String, Long> existingChunksAsMap() {

		File dir = new File(peerDir);
		if (!dir.exists())
			return null;

		HashMap<String, Long> chunksInfo = new HashMap<>();
		for (final File chunkEntry : dir.listFiles()) {
			if (serverFile.equals(chunkEntry.getName()))
				continue;
			chunksInfo.put(chunkEntry.getName(), 100L);
		}

		return chunksInfo;
	}

	private synchronized static HashMap<String, Long> getRequiredList(HashMap<String, Long> neighborChunksList) {
		HashMap<String, Long> out = (HashMap<String, Long>) neighborChunksList.clone();
		if (neighborChunksList != null && neighborChunksList.size() > 0) {
			HashMap<String, Long> primary = existingChunksAsMap();
			for (String key : primary.keySet()) {
				out.remove(key);
			}
		}
		return out;
	}

}
