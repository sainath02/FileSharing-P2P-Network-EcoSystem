package client;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Properties;
import java.util.logging.Logger;

@SuppressWarnings("unchecked")
public class Client {

	static Logger log = null;

	static {
		// Programmatic configuration
		System.setProperty("java.util.logging.SimpleFormatter.format", "%1$tM:%1$tS.%1$tL [%3$s] %5$s %6$s%n");
	}

	private static String serverHost = "localHost";
	static final String servergetList = "getList";
	static final String getSummary = "getSummary";
	static final String getTotalChunks = "getTotalChunks";
	static final String getBootInfo = "getBootInfo";
	static final String getNextChunk = "getNextChunk";
	static final String getChunkInfo = "getChunkInfo";
	static final String seperator = "#";
	static String formatSpecifier = "%05d";
	private String summaryFile = "Summary.txt";

	private int sPort = 8000;
	private int neighPort = -1;
	private int peerPort = -1;

	private int totalChunks = -1;
	private String fileName = null;
	private Long fileLength = -1L;
	private Integer totalChunksCtr = -1;
	private String peerDirPrefix = null;
	private boolean isBooted = false;
	String Name = null;

	Client(int serverPort, int peerPort, int neighPort) {
		this.sPort = serverPort;
		this.peerPort = peerPort;
		this.neighPort = neighPort;
	}

	public static void main(String args[]) throws Exception {

		Properties p = new Properties();
		// Read the properties file. If file is not found, exit.
		try (FileInputStream fis = new FileInputStream("config.properties")) {
			p.load(new FileInputStream("config.properties"));
		}

		if (p.size() == 0) {
			System.out.println("No properties found. Exiting");
			System.exit(0);
		}

		if (args.length > 0) {

			int sPort = Integer.parseInt(p.getProperty("0").split(",")[0]);

			String arrStr = p.getProperty(args[0]);
			String[] arr = arrStr.split(",");
			int peerPort = Integer.parseInt(arr[0]);
			String neighbor = arr[1];
			int neighPort = Integer.parseInt(p.getProperty(neighbor).split(",")[0]);

			Client c = new Client(sPort, peerPort, neighPort);
			c.startClient();
		} else {
			System.err.println("Please enter client Id as argument!!");
			System.exit(0);
		}
	}
	
	

	private void startClient() {
		System.out.println(String.format("Input: \nServer listening port \t: %d,\nPeer listening Port\t: %d, \nNeighbour port\t\t: %d", sPort, peerPort, neighPort));

		// Get few chunks from the server
		getChunksFromServer();

		while (!isBooted) {
			try {
				System.out.println("Please boot server first! Port : " + sPort);
				Thread.sleep(1000);
			} catch (InterruptedException e) {

			}
			getChunksFromServer();
		}

		System.out.println("Starting Download Thread!!");
		downloader.start();
		System.out.println("Starting Upload Thread!!");
		uploader.start();

	}

	private void getChunksFromServer() {

		HashMap<String, Long> myChunks = new HashMap<String, Long>();
		System.out.println("Requesting connect to " + serverHost + " at port " + sPort);
		try (Socket requestSocket = new Socket(serverHost, sPort);
				ObjectOutputStream out = new ObjectOutputStream(requestSocket.getOutputStream());
				ObjectInputStream in = new ObjectInputStream(requestSocket.getInputStream());) {
			isBooted = true;

			System.out.println("Connected to " + serverHost + " in port " + sPort);

			// You will get some information from server. Read that.
			// peerId + "\t" + fileName + "\t" + fileLength + "\t" + totalChunks
			String bootInfoStr = (String) in.readObject();
			String[] bootInfo = bootInfoStr.split("\t");
			String myId = bootInfo[0];

			Name = Client.class.getSimpleName() + myId;
			log = Logger.getLogger(Name);
			peerDirPrefix = "Files/" + Name + "/";

			fileName = bootInfo[1];
			fileLength = Long.parseLong(bootInfo[2].trim());
			// Get how many total chunks are present on the We need
			// to download these many number of chunks from server/peers.
			totalChunks = Integer.parseInt(bootInfo[3].trim());
			totalChunksCtr = totalChunks;
			log.info(String.format("File name: %s\nFile size: %d\nNumber of Chunks: %d", fileName, fileLength, totalChunks));

			out.writeObject("OKAY");
			out.flush();

			// Keep requesting new chunks from Download until server
			// provides new chunks. If the server does not have any new chunks
			// for this client then it will respond with "END"

			while (true) {
				out.writeObject(getNextChunk + seperator + Name);
				out.flush();

				String str = (String) in.readObject();

				if (str.equals("END"))
					break;

				String[] info = str.split(seperator);
				String chunkName = info[0];
				long size = Long.parseLong(info[1]);

				if (chunkName != null && size > 0) {

					File f = new File(peerDirPrefix + chunkName);

					// Request for the above received chunk.
					log.fine("Requesting chunk : " + chunkName);
					out.writeObject(chunkName);
					out.flush();

					// receive file
					long timeStamp = System.currentTimeMillis();
					f.getParentFile().mkdirs();
					try (OutputStream fos = new FileOutputStream(f)) {
						byte[] bytes = new byte[(int) size];
						int count;
						int totalBytes = 0;
						while ((count = in.read(bytes)) > 0) {
							totalBytes += count;
							log.finest("Writing " + count + " total : " + totalBytes + " size : " + size);
							fos.write(bytes, 0, count);
							if (totalBytes == size)
								break;
						}
						fos.flush();
						log.info(" receives chunk : " + chunkName + " from " + serverHost + ":" + sPort + " in " + (System.currentTimeMillis() - timeStamp) + "ms");
						myChunks.put(chunkName, size);
					}
				}
			}

			// Download random chunks from the Pass peer id to help
			// server differentiate.
			try (BufferedWriter writer = new BufferedWriter(new FileWriter(peerDirPrefix + summaryFile, true))) {
				for (String chunkName : myChunks.keySet()) {
					String entry = chunkName + seperator + myChunks.get(chunkName);
					writer.write(entry);
					writer.newLine();
					totalChunksCtr--;
				}
			}
		} catch (ConnectException ce) {
			System.err.println("Connection refused. You need to initiate a server first.");
		} catch (UnknownHostException uhe) {
			System.err.println("You are trying to connect to an unknown host!");
		} catch (IOException ioException) {
			ioException.printStackTrace();
		} catch (ClassNotFoundException cnfe) {
			cnfe.printStackTrace();
		}
	}

	Integer extractId(String fileName) {
		return fileName == null ? -1 : Integer.parseInt(fileName.replace(fileName + ".", ""));
	}

	synchronized private void appendToSummary(String chunkName, Long size) {
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(peerDirPrefix + summaryFile, true))) {
			totalChunksCtr--;
			writer.write(chunkName + seperator + size);
			writer.newLine();
			writer.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void mergeAndExit() throws Exception {
		// System converged. Merge all the files to recreate
		// the original file.
		OutputStream fos = new FileOutputStream(peerDirPrefix + fileName);
		for (int i = 1; i <= totalChunks; i++) {
			String fname = peerDirPrefix + fileName + "." + String.format(formatSpecifier, i);
			log.fine("Looking for file " + fname);
			File f = new File(fname);
			Files.copy(f.toPath(), fos);
		}
		fos.flush();
		log.info("Got all chunks. Download complete!!");
		fos.close();
		downloader.interrupt();
		System.in.read();
		System.in.read();
		System.in.read();
	}

	private HashMap<String, Long> summaryAsMap() {
		// Read the summary file.
		File f = new File(peerDirPrefix + summaryFile);
		if (f.exists()) {
			HashMap<String, Long> list = new HashMap<String, Long>();
			try {
				try (FileInputStream fis = new FileInputStream(f); BufferedReader br = new BufferedReader(new InputStreamReader(fis));) {
					String line = null;
					while ((line = br.readLine()) != null) {
						if (line.contains(seperator)) {
							String[] arr = line.split(seperator);
							list.put(arr[0], Long.parseLong(arr[1]));
						}
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}

			return list;
		}
		return null;
	}

	Thread downloader = new Thread() {
		public void run() {
			Socket neighSocket = null;
			try {
				// Try to make a connections to neighPort, If the connection
				// fails, sleep for 1 second and try again.
				boolean isConnected = false;
				while (!isConnected) {
					try {
						neighSocket = new Socket(serverHost, neighPort);
						isConnected = neighSocket.isConnected();
						log.info("Connected to " + serverHost + " in port " + neighPort);
					} catch (Exception e) {
						try {
							if (neighSocket == null || !neighSocket.isConnected()) {
								log.info("Cannot connect to download neighbor. Retry after 1 second.");
								Thread.sleep(1000);
							}
						} catch (Exception ex) {
							ex.printStackTrace();
						}
					}
				}

				// Able to connect to peer, now send your chunk list to the
				// neighbor and download them.
				try (ObjectOutputStream out = new ObjectOutputStream(neighSocket.getOutputStream()); ObjectInputStream in = new ObjectInputStream(neighSocket.getInputStream());) {
					while (true) {

						log.fine("Requesting chunk summary of " + neighSocket.toString() + " totalChunksCtr " + totalChunksCtr);
						log.info("DOWNLOAD: Request for chunks");
						out.writeObject(getSummary);
						out.flush();
						HashMap<String, Long> neighList = (HashMap<String, Long>) in.readObject();
						HashMap<String, Long> diffList = getDiffOfSummary(neighList);

						log.fine("Received summary. Found chunks: " + diffList);
						if (diffList.size() > 0) {
							log.info("DOWNLOAD: Found new chunks " + diffList);
						} else {
							log.info("DOWNLOAD: No new chunks yet.");
						}

						// Iteratively request and receive all the files.
						for (String chunkName : diffList.keySet()) {

							// Request for a chunk from the above difference of
							// summary list.
							log.info("DOWNLOAD: Get chunk : " + chunkName + " from download neighbor");
							out.writeObject(chunkName);
							out.flush();

							// Receive the actual file
							long timeStamp = System.currentTimeMillis();
							File f = new File(peerDirPrefix + chunkName);
							if (f.exists())
								continue;
							f.getParentFile().mkdirs();

							try (OutputStream fos = new FileOutputStream(f)) {
								long size = (long) diffList.get(chunkName);
								byte[] bytes = new byte[(int) size];
								int count;
								int totalBytes = 0;
								while ((count = in.read(bytes)) > 0) {
									totalBytes += count;
									log.finest("Writing " + count + " total : " + totalBytes + " size : " + size);
									fos.write(bytes, 0, count);
									if (totalBytes == diffList.get(chunkName))
										break;
								}
								fos.flush();
								log.fine("Created chunk : " + chunkName + " in " + (System.currentTimeMillis() - timeStamp));
								appendToSummary(chunkName, diffList.get(chunkName));
							}
						}

						// Check if the system downloaded all the chunks. If
						// yes, merge the files and stop download thread. Else,
						// sleep for 1 second and start downloading again.

						if (totalChunksCtr == 0) {
							mergeAndExit();
							break;
						} else {
							Thread.sleep(1000);
						}
					}
				} catch (IOException e) {
					log.info("DOWNLOAD: Peer disconnected");
					e.printStackTrace();
				}
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				if (neighSocket != null && neighSocket.isConnected())
					try {
						neighSocket.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
			}

		}

		private HashMap<String, Long> getDiffOfSummary(HashMap<String, Long> neighList) {
			HashMap<String, Long> diff = (HashMap<String, Long>) neighList.clone();
			if (neighList != null && neighList.size() > 0) {
				HashMap<String, Long> primary = summaryAsMap();
				for (String key : primary.keySet()) {
					diff.remove(key);
				}
			}
			return diff;
		}
	};

	Thread uploader = new Thread() {

		@Override
		public void run() {

			// We need only one connection for this socket. This peer uploads
			// data to only one specific neighbor.
			ServerSocket listener = null;
			try {
				// Code blocks here. Wait until another peer listens to you.
				listener = new ServerSocket(peerPort);
				log.info("UPLOAD: Ready for uploading to peer.");
				Socket con = listener.accept();
				try (ObjectInputStream in = new ObjectInputStream(con.getInputStream());
						ObjectOutputStream out = new ObjectOutputStream(con.getOutputStream());
						DataOutputStream d = new DataOutputStream(out)) {
					while (true) {
						String request = (String) in.readObject();
						if (request.equals(getSummary)) {
							log.info("UPLOAD: Sending summary to upload peer");
							out.writeObject(summaryAsMap());
						} else if (request.startsWith(fileName)) {
							File f = new File(peerDirPrefix + request);
							Files.copy(f.toPath(), d);
							d.flush();
							log.info("UPLOAD: Sent " + f.getName() + " to upload peer.");
						}
					}
				} catch (IOException e) {
				}
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				if (listener != null && !listener.isClosed())
					try {
						listener.close();
					} catch (Exception ex) {
						ex.printStackTrace();
					}
			}

		}

	};
}
