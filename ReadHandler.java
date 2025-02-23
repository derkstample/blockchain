import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class ReadHandler implements Runnable{
	private Socket s;
    private ObjectInputStream input;
    private BCNode node;
    private boolean running;
    final private boolean DEBUG;
	
	public ReadHandler(Socket s,ObjectInputStream input,BCNode node,final boolean DEBUG) throws IOException {
		this.s = s;
        this.input = input;
        this.node = node;
        this.DEBUG = DEBUG;
        running = true;
	}
	
	@Override
	public void run() {
        Block newlyMined = null; 

        while(running){
            try{
                //step 1: wait for a newly mined block to be read
                newlyMined = (Block) input.readObject();
                if(DEBUG) node.print("New block acquired: "+newlyMined);

                //step 2: verify the block
                if(node.validate(newlyMined)){

                    //step 3: add to the chain
                    node.getChain().add(newlyMined);

                    //step 4: send to all our peers
                    for(Socket s:node.getPeers()){
                        if(s!=this.s){ // don't send it to ourselves
                            if(DEBUG) node.print("Sending block to peer at port " + s.getLocalPort());
                            ObjectOutputStream os = node.getOutputStreams().get(s);
                            os.writeObject(newlyMined);
                            os.reset();
                        }
                    }
                } // implicit else: do nothing; note this takes care of 'ripples' where we send a Block and a peer sends it right back (it won't validate)

            }catch(Exception e){ // peer went down for one reason or another
                node.print("Connection with peer at port " +s.getLocalPort() +" lost, removing...");
                node.removePeer(s); // so just remove it from our list of peers
                running = false; // end this thread; its peer is dead
            }
        }
	}
}
