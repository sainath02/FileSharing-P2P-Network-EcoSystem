//package client;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
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
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.logging.Handler;
import java.util.logging.Logger;

//import server.Server;

public class Peer {

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

	public static void main(String[] args) {
		// String serverName = args[0];
		if(args.length != 3){
			System.out.println("Error in file input. Please provide server address, peer address, neighbour address in this order in batch file and rerun the program");
			return;
		}
		serverPort = Integer.parseInt(args[0]);
		clientPort = Integer.parseInt(args[1]);
		neighborPort = Integer.parseInt(args[2]);
//		serverPort = 8080;
//		clientPort = 8000;
//		neighborPort = 8000;

		peerDir = "PeerDB" + clientPort;
		
		log.info("Peer: " + hostName + ":" + clientPort + " Server: " + hostName + ":" + serverPort + " Neighbor: "
				+ hostName + ":" + neighborPort);

		getFileChunksFromServer();

		new NeighbourConnect().start();

		new ShareFile().start();

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
			out.flush();
			// System.out.println("Server says " + in.readInt());
			String peerInfoStr = (String) in.readUTF();
			System.out.println("Server says " + peerInfoStr);
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
				// Hard code chunkSize value
				byte[] bytes = new byte[chunkSize];

				int count;
				int totalBytes = 0;
				if(size < 0) size = chunkSize;
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
				
				updateChunksInfo(chunkName);

				//chunks.put(chunkName, (long)chunkSize);
				System.out.println("File is created");

				status++;
				nextChunk = peerId + status * noOfPeers;
			}
			if (chunks.size() == chunkscount) {
				buildClientFile();
			}

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

			int neighborRequestAttempts = 0;
			try {
				OutputStream outToServer = neighbor.getOutputStream();
				InputStream inFromServer = neighbor.getInputStream();

				DataOutputStream out = new DataOutputStream(outToServer);
				DataInputStream in = new DataInputStream(inFromServer);
				ObjectInputStream inObj = new ObjectInputStream(inFromServer);

				// chunkscount = 10;
				System.out.println(chunkscount);

				// chunksList = new ArrayList<>();
				// for (int i = 0; i < chunkscount; i++)
				// chunksList.add(i);

				while (true) {
					out.writeUTF("Get Chunks Information");
					HashMap<String, Long> neighborChunkList = (HashMap<String, Long>) inObj.readObject();

					// refactor below names "diffList" , "getDiffOfSummary"
					HashMap<String, Long> diffList = getDiffOfSummary(neighborChunkList);

					if (diffList.size() == 0) {
						if (neighborRequestAttempts >= 5) {
							//getFileChunksFromServer();
						} else {
							neighborRequestAttempts++;
							Thread.sleep(5000);
							continue;
						}
					}

					neighborRequestAttempts = 0;
					log.info("Neighbour has " + diffList.size() + " different file shared");
					for(String s : neighborChunkList.keySet()){
						System.out.println(s);
					}

					for (String next : diffList.keySet()) {

						System.out.println("Requesting file from Neighbour: " + next);
						
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
						
						// System.out.println("Recidue "+in.readUTF());
						while ((count = in.read(bytes)) >= 0) {
							totalBytes += count;
							System.out.println("Writing " + count + " total : " + totalBytes + " size : " + size);
							fos.write(bytes, 0, count);
							System.out.println("Total Bytes: " + totalBytes);
							System.out.println("chunkSize " + size);
							if (totalBytes >= size) {
								System.out.println("Copied complete file");
								break;
							}
						}
						fos.flush();
						updateChunksInfo(next);
						//chunks.put(next, (long) 100);
						System.out.println("File is created");
						log.info("Current residing files are");
						for(String s : chunks.keySet()){
							System.out.println(s);
						}
						
					}
					
					if (chunks.size() == chunkscount) {
						buildClientFile();
						break;
					}
				}
				
			} catch (IOException e) {
				e.printStackTrace();
			} catch (ClassNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} finally{
				if(neighbor != null && neighbor.isConnected()){
					try {
						neighbor.close();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}

			if (chunks.size() == chunkscount) {
				try {
					buildClientFile();
					log.info("File " + serverFile + " has be built in peerDB");
					return;
				} catch (IOException e) {
					// TODO Auto-generated catch block
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
				// TODO Auto-generated catch block
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
								obj.writeObject(summaryAsMap());
//								HashMap<String, Long> tempMap = new HashMap<>();
//								tempMap.put("serverFile.txt.3", 100L);
//								tempMap.put("serverFile.txt.1", 100L);
//								tempMap.put("serverFile.txt.2", 100L);
//								tempMap.put("serverFile.txt.4", 100L);
//								tempMap.put("serverFile.txt.6", 100L);
//								tempMap.put("serverFile.txt.7", 100L);
//								tempMap.put("serverFile.txt.8", 100L);
//								//tempMap.put("serverFile.txt.9", 100L);
//								obj.writeObject(tempMap);
							} else if(request.startsWith("Get details of chunk")){
			    				System.out.println("Request Message is: "+request);
			    				String filename = request.split("#")[1].trim();
			    				File f = new File(peerDir, filename);
			    				String response = peerId + "#"+ filename + "#" + f.length();
			    				out.writeUTF(response);
			    				out.flush();
			    			} else if (request.startsWith("Get File")) {
								System.out.println("Request Message is: " + request);
								String filename = request.split("#")[1].trim();
								log.info("Sending file with name " + filename);
								//Change below dir name to peerDir
								File f = new File(peerDir, filename);

								int count;
								byte[] buffer = new byte[chunkSize];
								OutputStream o = neighbor.getOutputStream();
								// BufferedInputStream i = new
								// BufferedInputStream(new FileInputStream(f));

								FileInputStream i = new FileInputStream(f);

								while ((count = i.read(buffer)) > 0) {
									o.write(buffer, 0, count);
									o.flush();
								}
								log.info("Sent file to neighbour");
								// server.close();
							} else if (request.startsWith("close")) {
								log.info("Neighbour " + neighbor.getRemoteSocketAddress()
										+ " has requested to close connection");
								System.out.println("Closing connection");
								neighbor.close();
								break;
							}
							// These lines should be commented. You get file
							// count from server.
							else if (request.startsWith("Count of Chunks")) {
								System.out.println("Request Message is: " + request);
								out.writeInt(10);
								out.flush();
							}

						} catch (Exception e) {
							// Exception ignored
						}
					}

				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}
	}
	
	private synchronized static void updateChunksInfo(String chunkName){
		chunks.put(chunkName, (long)100);
	}

	// Refactor the name of SummaryAsMap
//	private synchronized static HashMap<String, Long> summaryAsMap() {
//		return chunks;
//	}
	private synchronized static HashMap<String, Long> summaryAsMap() {
		
		File dir = new File(peerDir);
		if (!dir.exists()) return null;
		
		HashMap<String, Long> chunksInfo = new HashMap<>();
		for(final File chunkEntry : dir.listFiles()){
			if(serverFile.equals(chunkEntry.getName())) continue;
			chunksInfo.put(chunkEntry.getName(), 100L);
		}

		return chunksInfo;
	}

	private synchronized static HashMap<String, Long> getDiffOfSummary(HashMap<String, Long> neighborChunksList) {
		HashMap<String, Long> out = (HashMap<String, Long>) neighborChunksList.clone();
		if (neighborChunksList != null && neighborChunksList.size() > 0) {
			HashMap<String, Long> primary = summaryAsMap();
			for (String key : primary.keySet()) {
				out.remove(key);
			}
		}
		return out;
	}

}
