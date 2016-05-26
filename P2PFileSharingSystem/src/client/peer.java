package client;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class peer {
	
	public static void main(String [] args)
	   {
	      //String serverName = args[0];
	      //int port = Integer.parseInt(args[1]);
	      int port = 8080;
	      String serverName = "localhost";
	      
	      File dir = new File("peerDB");
	      dir.mkdir();
	      
	      try
	      {
	         System.out.println("Connecting to " + serverName +" on port " + port);
	         Socket client = new Socket(serverName, port);
	         System.out.println("Just connected to " + client.getRemoteSocketAddress());
	         
	         OutputStream outToServer = client.getOutputStream();
	         InputStream inFromServer = client.getInputStream();
	         
	         DataOutputStream out = new DataOutputStream(outToServer);
	         DataInputStream in = new DataInputStream(inFromServer);
	         
	         out.writeUTF("Hello from " + client.getLocalSocketAddress());
	         System.out.println("Server says " + in.readInt());
	         
	         String chunkname = "serverFile.txt.1";
	         out.writeUTF("Get File"+ "#" + chunkname);
	         
	         System.out.println("Completed 2nd request");
	         
	         File f = new File(dir, chunkname);
		     f.createNewFile();
		     FileOutputStream fos = new FileOutputStream(f);
		     byte[] bytes = new byte[server.Server.chunkSize];
		     
		     int count;
		     int totalBytes = 0;
		     int size = server.Server.chunkSize;
		     while ((count = in.read(bytes)) >= 0){
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
		     
		     System.out.println("Closing the connection to server...");
		     out.writeUTF("close");
		     client.close();
	      }catch(IOException e)
	      {
	         e.printStackTrace();
	      }
	   }
	
}
