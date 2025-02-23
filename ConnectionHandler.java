import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class ConnectionHandler implements Runnable{
    private ServerSocket ss;
    private BCNode node;
    private final boolean DEBUG;
    
    public ConnectionHandler(int port,BCNode node,final boolean DEBUG) throws IOException {
		this.ss = new ServerSocket(port);
        this.node = node;
        this.DEBUG = DEBUG;
	}
	
	@Override
	public void run() {
        while(true){
            try{
                // wait for incoming connections
                Socket incomingConnection = ss.accept();
                if(DEBUG) node.print("Incoming connection from peer at port " +incomingConnection.getLocalPort());

                // setup ObjectStreams and update the BCNode with all the peer's info
                node.getPeers().add(incomingConnection);
                ObjectInputStream nodeIn = new ObjectInputStream(incomingConnection.getInputStream()); // input/output ObjectStreams must be instantiated in alternating order amongst peers to prevent deadlock
                ObjectOutputStream nodeOut = new ObjectOutputStream(incomingConnection.getOutputStream());
                node.getInputStreams().put(incomingConnection,nodeIn); // put the streams in the node for other threads
                node.getOutputStreams().put(incomingConnection,nodeOut);

                // give the new peer the current state of the chain
                nodeOut.writeObject(node.getChain());
                nodeOut.reset(); // reset cache

                // fork thread and make new ReadHandler
                if(DEBUG) node.print("Starting new thread for peer at port " + incomingConnection.getLocalPort());
                ReadHandler rh = new ReadHandler(incomingConnection, nodeIn, node, DEBUG);
                Thread th = new Thread(rh);
                th.start();

            }catch(Exception e){
                node.print("Error establishing connection to peer"); // non-fatal
            }
        }
	}
}
