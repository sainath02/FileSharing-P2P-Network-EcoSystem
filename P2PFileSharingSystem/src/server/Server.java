package server;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Hashtable;
import java.util.logging.Logger;


public class Server {

	static {
		// Programmatic configuration
		System.setProperty("java.util.logging.SimpleFormatter.format", "%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS.%1$tL %4$-7s [%3$s] (%2$s) %5$s %6$s%n");
	}
	static final Logger log = Logger.getLogger(Server.class.getName());
	
	private static int numOfChunks = 5;
	public static String filename = "serverFile.txt";
	public static String data = "I returned from the City about three o'clock on that \nMay afternoon pretty well disgusted with life. \nI had been three months in the Old Country, and was \nfed up with it. \nIf anyone had told me a year ago that I would have \nbeen feeling like that I should have laughed at him; \nbut there was the fact. \nThe weather made me liverish, \nthe talk of the ordinary Englishman made me sick, \nI couldn't get enough exercise, and the amusements \nof London seemed as flat as soda-water that \nhas been standing in the sun. \n'Richard Hannay,' I kept telling myself, 'you \nhave got into the wrong ditch, my friend, and \nyou had better climb out.";
	private static final int chunkSize = 100 * 1024; //Each chunk having 100KB
	private static Hashtable<String, Long> chunks = new Hashtable<>();
	
	public static void main(String args[]){
		System.out.println("Hello Server. Please Upload ur file into Peer System");
		
		numOfChunks = Integer.parseInt(args[0]);
		
		createAndBreakFile(numOfChunks);
		try {
			File newFile = createFile(numOfChunks);
			System.out.println("Parent: "+ newFile.getParent());
			breakFile(newFile, numOfChunks);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		System.out.println("Distributed file can now be shared into peer to peer system");
	}
	
	private static void createAndBreakFile(int numOfChunks){
		
	}
	
	private static File createFile(int numOfChunks) throws IOException{
		File dir = new File("ServerDB");
		dir.mkdir();
		File f = new File(dir, filename);
		f.createNewFile();
		
		//Write if condition here
		if(!f.exists() || f.length() == 0){
			FileOutputStream fos = new FileOutputStream(f);
			byte[] dataBytes = (data + " \n" + String.format("%08d", 0)).getBytes();
			long m = (numOfChunks * chunkSize)/dataBytes.length;
			
			for(long i = 0; i< m; i++){
				String ctr = String.format("%08d", i);
				dataBytes = (ctr + " "+data + "\n").getBytes();
				fos.write(dataBytes, 0, dataBytes.length);
			}
			
			fos.write(dataBytes, 0, (int)((numOfChunks * chunkSize)%dataBytes.length));
			fos.flush();
			fos.close();
			log.info("Created Server file: " + f.getName() + " of size:  "+ f.length() + " bytes");
		}
		
		//f.delete();
		//dir.delete();
		return f;
	}
	
	private static void breakFile(File newFile, int numOfChunks2) throws FileNotFoundException {
		// TODO Auto-generated method stub
		if(newFile.length() == 0){
			System.err.println("File size is 0");
			return ;
		}
		
		int chunkNum = 0;
		byte[] chunkBytes = new byte[chunkSize];
		try {
			BufferedInputStream bis = new BufferedInputStream(new FileInputStream(newFile));
			int temp = 0;
			while ((temp = bis.read(chunkBytes)) > 0){
				File chunk = new File(newFile.getParent(), newFile.getName()+"."+(chunkNum++));
				FileOutputStream fos = new FileOutputStream(chunk);
				fos.write(chunkBytes, 0, temp);
				chunks.put(chunk.getName(), chunk.length());
				log.info("Created chunk file: "+ chunk.getName() + " of size: "+ chunk.length());
				fos.close();
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
 
}
