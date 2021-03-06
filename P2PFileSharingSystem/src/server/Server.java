package server;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.util.Hashtable;
import java.util.Properties;
import java.util.logging.Handler;
import java.util.logging.Logger;

import jdk.nashorn.internal.runtime.regexp.joni.Config;

public class Server {

	static {
		// Program log configuration
		System.setProperty("java.util.logging.SimpleFormatter.format",
				"%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS.%1$tL %4$-7s (%2$s) %5$s %6$s%n");
	}
	static final Logger log = Logger.getLogger(Server.class.getName());

	private static int numOfChunks;
	private static int numOfPeers = 5;
	public static String filename;
	public static String serverDir = "ServerDB";
	public static String data = "I returned from the City about three o'clock on that \nMay afternoon pretty well disgusted with life. \nI had been three months in the Old Country, and was \nfed up with it. \nIf anyone had told me a year ago that I would have \nbeen feeling like that I should have laughed at him; \nbut there was the fact. \nThe weather made me liverish, \nthe talk of the ordinary Englishman made me sick, \nI couldn't get enough exercise, and the amusements \nof London seemed as flat as soda-water that \nhas been standing in the sun. \n'Richard Hannay,' I kept telling myself, 'you \nhave got into the wrong ditch, my friend, and \nyou had better climb out.";
	public static final int chunkSize = 100 * 1024; // Each chunk having 100KB
	private static Hashtable<String, Long> chunks = new Hashtable<>();
	private static int serverPort = 8080;

	public static void main(String args[]) throws Exception {

		System.out.println("Hello USER. Setting configurations and uploading your file into peer system");

		Properties properties = new Properties();
		System.out.println();

		// Taking server properties from configuration file.
		FileInputStream fs = new FileInputStream("src/server/config.properties");
		properties.load(fs);
		if (properties.isEmpty())
			log.info(
					"No server properties are found. System runs with default setting with program generated test file. Server uses port # 8080");

		String serverProperties = properties.getProperty("0");
		String[] propArray = serverProperties.split(",");

		switch (propArray.length) {
		case 3:
			numOfPeers = Integer.parseInt(propArray[2]);
		case 2:
			filename = propArray[1];
		case 1:
			if (!propArray[0].equals("")){
				serverPort = Integer.parseInt(propArray[0]);
				break;
			}
		default:
			filename = "serverFile.txt";
			numOfChunks = 10;
		}

		log.info("==========SERVER PROPERTIES===========");
		log.info("filename: " + filename + "; server port: " + serverPort + "; Number of clients: " + numOfPeers);

		if (propArray.length < 2)
			createAndBreakFile(numOfChunks);
		else {
			File f = new File(filename);
			if (!f.exists()) {
				System.err.println(
						"The input file doesn't exits. Please input correct file location and rerun the program");
			}
			breakInputFileIntoChunks(f);
		}

		ServerSocket sSocket = new ServerSocket(serverPort);
		log.info("Started server socket");

		int clientNum = 0;
		try {
			while (true) {
				new ServeFile(sSocket.accept(), clientNum).start();
				System.out.println("Client " + clientNum + " is connected!");
				clientNum++;
			}
		} finally {
			sSocket.close();
			System.out.println("File is distributed successfully into peer to peer system");
		}

	}

	private static void createAndBreakFile(int numOfChunks) {

		try {
			File newFile = createFile(numOfChunks);
			System.out.println("Parent: " + newFile.getParent());
			breakFile(newFile, numOfChunks);
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	private static File createFile(int numOfChunks) throws IOException {

		File dir = new File(serverDir);
		if (dir.exists())
			deleteDirectory(dir);

		dir.mkdir();
		File f = new File(dir, filename);
		f.createNewFile();

		if (!f.exists() || f.length() == 0) {
			FileOutputStream fos = new FileOutputStream(f);
			byte[] dataBytes = (data + " \n" + String.format("%08d", 0)).getBytes();
			long m = (numOfChunks * chunkSize) / dataBytes.length;

			for (long i = 0; i < m; i++) {
				String ctr = String.format("%08d", i);
				dataBytes = (ctr + " " + data + "\n").getBytes();
				fos.write(dataBytes, 0, dataBytes.length);
			}

			fos.write(dataBytes, 0, (int) ((numOfChunks * chunkSize) % dataBytes.length));
			fos.flush();
			fos.close();
			log.info("Created Server file: " + f.getName() + " of size:  " + f.length() + " bytes");
		}

		return f;

	}

	private static void breakFile(File newFile, int numOfChunks2) throws FileNotFoundException {

		if (newFile.length() == 0) {
			System.err.println("File size is 0. No content to share.");
			return;
		}

		int chunkNum = 0;
		byte[] chunkBytes = new byte[chunkSize];
		try {
			BufferedInputStream bis = new BufferedInputStream(new FileInputStream(newFile));
			int temp = 0;
			while ((temp = bis.read(chunkBytes)) > 0) {
				File chunk = new File(newFile.getParent(), newFile.getName() + "." + (chunkNum++));
				FileOutputStream fos = new FileOutputStream(chunk);
				fos.write(chunkBytes, 0, temp);
				chunks.put(chunk.getName(), chunk.length());

				log.info("Created chunk file: " + chunk.getName() + " of size: " + chunk.length());
				fos.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	// Takes input file, copies file to Server buffer directory and breaks
	// the file into chunks.
	public static void breakInputFileIntoChunks(File sourceFile) throws IOException {

		File dir = new File(serverDir);
		if (dir.exists())
			deleteDirectory(dir);

		dir.mkdir();
		File destiFile = new File(dir, sourceFile.getName());
		Files.copy(sourceFile.toPath(), destiFile.toPath());

		int chunkNum = 0;
		byte[] chunkBytes = new byte[chunkSize];
		try {
			BufferedInputStream bis = new BufferedInputStream(new FileInputStream(destiFile));
			int temp = 0;
			while ((temp = bis.read(chunkBytes)) > 0) {
				File chunk = new File(destiFile.getParent(), destiFile.getName() + "." + (chunkNum++));
				FileOutputStream fos = new FileOutputStream(chunk);
				fos.write(chunkBytes, 0, temp);
				chunks.put(chunk.getName(), chunk.length());

				log.info("Created chunk file: " + chunk.getName() + " of size: " + chunk.length());
				fos.close();
			}
			numOfChunks = chunkNum;
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	// Deletes the existing data in server buffer directory.
	private static void deleteDirectory(File dir) {

		String[] entries = dir.list();
		for (String s : entries) {
			File currentFile = new File(dir.getPath(), s);
			currentFile.delete();
		}
		dir.delete();

	}

	// A thread is assigned to each peer that request file from server.
	private static class ServeFile extends Thread {

		private Socket server;
		private int peerId;
		private int status = 0;

		public ServeFile(Socket connection, int no) {
			System.out.println("Connecting to peer");
			this.server = connection;
			this.peerId = no;
		}

		public void run() {
			DataInputStream in;
			DataOutputStream out;

			try {
				while (true) {
					System.out.println("Listening to peer " + peerId);

					in = new DataInputStream(server.getInputStream());
					out = new DataOutputStream(server.getOutputStream());

					// Continuously listens to peer until a request.
					String request;
					while (true) {
						try {
							request = in.readUTF();
							break;
						} catch (Exception e) {
							// Exception ignored
						}
					}

					if (request.startsWith("Hello")) {
						// Sends File details and peers information.
						log.info("PEER" + peerId + " Request Message is: " + request);
						String response = peerId + "#" + filename + "#" + chunks.size() + "#" + numOfPeers;
						out.writeUTF(response);
						out.flush();
					}

					else if (request.startsWith("Count of Chunks")) {
						// Sends the integer of number of chunks
						log.info("PEER" + peerId + " Request Message is: " + request);
						out.writeInt(chunks.size());
						out.flush();
					}

					else if (request.startsWith("Get next chunk name")) {
						// Sends the next chunk name to be transferred. If all
						// the chunks respective to peer are transferred, send a
						// message to collect chunks from peers.
						log.info("PEER" + peerId + " Request Message is: " + request);
						int nextFile = peerId + status * numOfPeers;
						if (nextFile < numOfChunks) {
							out.writeUTF(filename + "." + nextFile);
							out.flush();
							status++;
						} else {
							out.writeUTF("Collect from peers");
							out.flush();
						}
					}

					else if (request.startsWith("Get details of chunk")) {
						// Sends chunk information.
						log.info("PEER" + peerId + " Request Message is: " + request);
						String filename = request.split("#")[1].trim();
						File f = new File(serverDir, filename);
						String response = peerId + "#" + filename + "#" + f.length();
						out.writeUTF(response);
						out.flush();
					}

					else if (request.startsWith("Get File")) {
						// sends chunks data.
						log.info("PEER" + peerId + " Request Message is: " + request);
						String filename = request.split("#")[1].trim();

						log.info("Sending file with name " + filename);
						File f = new File(serverDir, filename);

						int count;
						byte[] buffer = new byte[chunkSize];
						OutputStream o = server.getOutputStream();
						BufferedInputStream i = new BufferedInputStream(new FileInputStream(f));

						while ((count = i.read(buffer)) > 0) {
							o.write(buffer, 0, count);
							o.flush();
						}
						log.info("Sent file to peer");
						// server.close();
					}

					else if (request.startsWith("close")) {
						// Requested to close the connection
						log.info("PEER" + peerId + " / " + server.getRemoteSocketAddress()
								+ " : Requested to close connection");
						System.out.println("Closing connection with peer " + peerId);
						server.close();
						break;
					}

				}

			} catch (Exception e1) {
				e1.printStackTrace();
			} finally {
				System.out.println("No request from client");
				System.out.println("Finally Server stopped listening to peers");
			}

		}

	}

}
