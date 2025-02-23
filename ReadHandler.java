import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class ReadHandler implements Runnable{
	private Socket s;
    private ObjectInputStream input;
    private BCNode node;
	
	public ReadHandler(Socket s,ObjectInputStream input,BCNode node) throws IOException {
		this.s = s;
        this.input = input;
        this.node = node;
	}
	
	@Override
	public void run() {
        while(true){
            try{
                //step 1: wait for a newly mined block to be read
                Block newlyMined = (Block) input.readObject();
                //step 2: verify the block
                if(node.validate(newlyMined)){
                    //step 3: add to the chain
                    node.getChain().add(newlyMined);
                    //step 4: send to all our peers
                    for(Socket s:node.getPeers()){
                        if(s!=this.s){ // don't send it to ourselves
                            ObjectOutputStream os = node.getOutputStreams().get(s);
                            os.writeObject(newlyMined);
                            os.reset();
                        }
                    }
                } // implicit else: do nothing

            }catch(Exception e){
                e.printStackTrace(); // TODO: clean this up
                System.exit(1);
            }
        }
	}
}
