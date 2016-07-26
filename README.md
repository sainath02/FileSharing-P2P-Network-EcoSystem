# FileSharing-P2P-Network-EcoSystem

Project is a implementation of special kind of Peer to Peer network architeture. This model is used when you have a file on demand (or) a hugh file to share over P2P network. 
Server(Peer sharing the file) breaks the file into chunks. Chunk size can be configured in the program. Now peer starts uploading its chunks, while downloading missing chunks from other peers.
This type of network is usually implemented in Bit-Torrent like FileSharing Systems. Unlike bit-torrent a server or a peer can server its file to any number of peers at the sametime.


# Server.java
1. Server takes server Port number 'config.properties'. By default the server program is run on 'localhost' 8080 port.
2. Server takes file name from 'config.properties'. If not file is specified, server builds its own test file with file name as 'serverFile.txt'.
3. Server copies the file from Specified path to its buffer location in '/ServerDB/'
4. It then breaks this file into chunks and starts serving the chunks to PEER System.

# Peer.java
1. Each peer takes three properties from the arguments.
	Server port number: Port from which client/peer listens to server and download few of the chunk files.
	peer port number: Port used by client to upload its files to its neighbour peers.
	neighbour port number: Port from which client listens to its peer neighbour and download few of the chunk files.
2. Each peer creates their own Buffer directories to where the file is downloaded.
3. Each peer first downloads few of the chunks from server.
4. Then a thread is started to constantly request its neighbour for missing chunks.
5. Symultaniously another thread is run to serve the existing chunks files into PEER system.
6. After client receives all the chunks, it builds the actual file in the same directory from its chunks.