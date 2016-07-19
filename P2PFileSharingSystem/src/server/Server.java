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
import java.nio.file.Files;
import java.util.Hashtable;
import java.util.logging.Handler;
import java.util.logging.Logger;

public class Server {

	static {
		// Programmatic configuration
		System.setProperty("java.util.logging.SimpleFormatter.format",
				"%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS.%1$tL %4$-7s [%3$s] (%2$s) %5$s %6$s%n");
	}
	static final Logger log = Logger.getLogger(Server.class.getName());

	private static int numOfChunks = 10;
	private static int numOfPeers = 5;
	public static String filename = "serverFile.txt";
	public static String serverDir = "ServerDB";
	public static String data = "I returned from the City about three o'clock on that \nMay afternoon pretty well disgusted with life. \nI had been three months in the Old Country, and was \nfed up with it. \nIf anyone had told me a year ago that I would have \nbeen feeling like that I should have laughed at him; \nbut there was the fact. \nThe weather made me liverish, \nthe talk of the ordinary Englishman made me sick, \nI couldn't get enough exercise, and the amusements \nof London seemed as flat as soda-water that \nhas been standing in the sun. \n'Richard Hannay,' I kept telling myself, 'you \nhave got into the wrong ditch, my friend, and \nyou had better climb out.";
	public static final int chunkSize = 100 * 1024; // Each chunk having 100KB
	private static Hashtable<String, Long> chunks = new Hashtable<>();
	private static int serverPort = 8080;

	public static void main(String args[]) throws Exception {
		System.out.println("Hello Server. Please Upload ur file into Peer System");

		numOfChunks = Integer.parseInt(args[0]);

		createAndBreakFile(numOfChunks);

		// int serverPort = 8080;
		ServerSocket sSocket = new ServerSocket(serverPort);
		System.out.println("Started server socket");

		int clientNum = 0;
		try {
			while (true) {
				new ServeFile(sSocket.accept(), clientNum).start();
				System.out.println("Client " + clientNum + " is connected!");
				clientNum++;
			}
		} finally {
			sSocket.close();
			System.out.println("Distributed file is shared into peer to peer system");
		}

		// ServeFile sf = new ServeFile();
		// Thread t = new Thread(sf);
		// t.start();

		// try {
		// System.out.println("Joining to main thread");
		// t.join();
		// } catch (InterruptedException e) {
		// System.out.println("Main Thread has been interrupted");
		// e.printStackTrace();
		// }
		//
		// System.out.println("Distributed file can now be shared into peer to
		// peer system");
	}

	private static void createAndBreakFile(int numOfChunks) {
		try {
			File newFile = createFile(numOfChunks);
			System.out.println("Parent: " + newFile.getParent());
			breakFile(newFile, numOfChunks);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private static File createFile(int numOfChunks) throws IOException {
		File dir = new File(serverDir);
		dir.mkdir();
		File f = new File(dir, filename);
		f.createNewFile();

		// Write if condition here
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

		// f.delete();
		// dir.delete();
		return f;
	}

	private static void breakFile(File newFile, int numOfChunks2) throws FileNotFoundException {
		// TODO Auto-generated method stub
		if (newFile.length() == 0) {
			System.err.println("File size is 0");
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
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

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
				//Socket server;
				DataInputStream in;
				DataOutputStream out;
		        //int peerId = 1;
		        try{
		        	while(true){
		        		System.out.println("Listening to client");
		    			//System.out.println("Connected to "+ server.getRemoteSocketAddress());
		    			
		    			in = new DataInputStream(server.getInputStream());
		    			out = new DataOutputStream(server.getOutputStream());
		    				    	
		    			String request;
		    			while(true){
		    				try {
		    					request = in.readUTF();
		    					break;
		    				}catch(Exception e){
		    					//Exception ignored
		    				}
		    			}
		    			
		    			if(request.startsWith("Hello")){
		    				System.out.println("Request Message is: "+request);
		    				String response = peerId + "#"+ filename + "#" + chunks.size() + "#" + numOfPeers;
		    				//out.writeInt(chunks.size());
		    				out.writeUTF(response);
		    				out.flush();
		    			}
		    			else if(request.startsWith("Count of Chunks")){
		    				System.out.println("Request Message is: "+request);
		    				out.writeInt(chunks.size());
		    				out.flush();
		    			}
		    			//Complete below logic
		    			else if(request.startsWith("Get next chunk name")){
		    				System.out.println("Request Message is: "+request);
		    				int nextFile = peerId+status * numOfPeers;
		    				if(nextFile < numOfChunks){
		    					out.writeUTF(filename+"."+nextFile);
		    					out.flush();
		    					status++;
		    				}
		    				else{
		    					out.writeUTF("Collect from peers");
		    					out.flush();
		    				}
		    			}
//		    			else if(request.startsWith("Get Next Chunk Name")){
//		    				System.out.println("Request Message is: "+request);
//		    				out.writeUTF("serverFile.txt");
//		    				//out.writeInt(chunks.size());
//		    			}
		    			else if(request.startsWith("Get File")){
		    				System.out.println("Request Message is: "+request);
		    				String filename = request.split("#")[1].trim();
		    				log.info("Sending file with name "+ filename);
		    				File f = new File(serverDir,filename);
		    				
		    				int count;
		    				byte[] buffer = new byte[chunkSize];
		    				OutputStream o = server.getOutputStream();
		    				BufferedInputStream i = new BufferedInputStream(new FileInputStream(f));
		    				
		    				while ((count = i.read(buffer)) > 0) {
		    				     o.write(buffer, 0, count);
		    				     o.flush();
		    				}
		    				log.info("Sent file to peer");
		    				//server.close();
		    			}
		    			else if(request.startsWith("close")){
		    				log.info("Peer "+ server.getRemoteSocketAddress() + " has requested to close connection");
		    				System.out.println("Closing connection");
		    				server.close();
		    				break;
		    			}
		    			
		        	}
		        	
		        } catch (Exception e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
		        
				finally {
					//sSocket.close();
					System.out.println("No request from client");
					System.out.println("Finally Server stopped listening to peers");
				}		    
			
		}

	}
}
