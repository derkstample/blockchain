import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Inet4Address;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class BCNode {
    private List<Block> chain;
    private List<Socket> peers;
    private Map<Socket,ObjectInputStream> inputStreams;
    private Map<Socket,ObjectOutputStream> outputStreams;
    
    final private int N = 5;
    final private boolean DEBUG = true; // true to print debug info

    public static void main(String[] args) {
        Scanner keyScan = new Scanner(System.in);
        
        // Grab my port number on which to start this node
        System.out.print("Enter port to start (on current IP): ");
        int myPort = keyScan.nextInt();
        
        // Need to get what other Nodes to connect to
        System.out.print("Enter remote ports (current IP is assumed): ");
        keyScan.nextLine(); // skip the NL at the end of the previous scan int
        String line = keyScan.nextLine();
        List<Integer> remotePorts = new ArrayList<Integer>();
        if (line != "") {
            String[] splitLine = line.split(" ");
            for (int i=0; i<splitLine.length; i++) {
                remotePorts.add(Integer.parseInt(splitLine[i]));
            }
        }
        // Create the Node
        BCNode n = new BCNode(myPort, remotePorts);

        String ip = "";
        try {
             ip = Inet4Address.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            e.printStackTrace();
            System.exit(1);
        }
        
        System.out.println("Node started on " + ip + ": " + myPort);
        
        // Node command line interface
        while(true) {
            System.out.println("\nNODE on port: " + myPort);
            System.out.println("1. Display Node's blockchain");
            System.out.println("2. Create/mine new Block");
            System.out.println("3. Kill Node");
            System.out.print("Enter option: ");
            int in = keyScan.nextInt();
            
            if (in == 1) {
                System.out.println(n);
                
            } else if (in == 2) {
                // Grab the information to put in the block
                System.out.print("Enter information for new Block: ");
                String blockInfo = keyScan.next();
                Block b = new Block(blockInfo);
                n.addBlock(b);
                
            } else if (in == 3) {
                // Take down the whole virtual machine (and all the threads)
                //   for this Node.  If we just let main end, it would leave
                //   up the Threads the node created.
                keyScan.close();
                System.exit(0);
            }
        }
    }

    @SuppressWarnings("unchecked") // objectstream type casting warnings were annoying
    public BCNode(int port, List<Integer> peerPorts){
        if(DEBUG) System.out.println("Creating new BCNode");
        int numPeers = peerPorts.size();
        peers = new ArrayList<Socket>();
        inputStreams = new HashMap<Socket,ObjectInputStream>();
        outputStreams = new HashMap<Socket,ObjectOutputStream>();

        // first, set up ReadHandler threads for each of our given peer nodes (or start the chain if no peers are given)
        if(numPeers > 0){ // if we have at least one peer to get the chain from
            if(DEBUG) System.out.println("Attempting to start peer connections");
            // in order to prevent bad actors from corrupting the chain by sending invalid blocks to new peers, we need to implement a consensus algorithm that uses majority rule to decide the current state of the chain
            // I chose to do this by just keeping track of what each peer thinks the chain looks like and making a histogram to compare them
            ArrayList<ArrayList<Block>> consensusChains = new ArrayList<ArrayList<Block>>();
            for(int i=0;i<numPeers;i++){
                try {
                    Socket s = new Socket("localhost",peerPorts.get(i));
                    peers.add(s);
                    ObjectOutputStream output = new ObjectOutputStream(s.getOutputStream()); //instantiating the input/output streams between peers is necessary to prevent deadlock!
                    ObjectInputStream input = new ObjectInputStream(s.getInputStream());
                    inputStreams.put(s,input);
                    outputStreams.put(s,output);
                    // the ConnectionHandler will send us the chain as soon as a peer connects, so we need to acquire it here before we begin the ReadHandler as normal
                    consensusChains.add((ArrayList<Block>) input.readObject());
                    
                    // now setup a ReadHandler to read any input from the peer in its own thread
                    ReadHandler rh = new ReadHandler(s,input,this);
                    Thread th = new Thread(rh);
                    th.start(); // for some reason I thought I just needed to call th.run() and that bug was singlehandidly the most time consuming part of this project because I was too stubborn to read the javadocs for Thread
                    if(DEBUG) System.out.println("Connected (" + peers.size() + " nodes connected)");
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                    System.out.println("Could not connect to node " + peerPorts.get(i) + ", skipping...");
                }
            }
            // lastly determine which chain we read in is actually the correct one using a majority consensus
            chain = determineConsensus(consensusChains);
        }else{ // otherwise we must create the whole chain
            Block genesisBlock = new Block();
            chain = new ArrayList<Block>();
            chain.add(genesisBlock);
        }
        // now we can start the ConnectionHandler to create a ServerSocket
        if(DEBUG) System.out.println("Attempting to start ConnectionHandler");
        try{
            ConnectionHandler ch = new ConnectionHandler(port, this);
            Thread th = new Thread(ch);
            th.start();
        }catch(IOException e){
            System.out.println("Failed starting ConnectionHandler!");
            System.exit(1);
        }
        if(DEBUG) System.out.println("ConnectionHandler started");

    }

    public void addBlock(Block b){
        // step 1: give the block the chain's tailing hash
        b.setPrevHash(chain.getLast().getHash());
        // step 2: mining
        String prefixZeros = new String(new char[N]).replace('\0','0');
        String candidateHash = b.calculateBlockHash();
        int nonce = b.getNonce();
        while(!candidateHash.substring(0,N).equals(prefixZeros)){
            nonce++;
            b.setNonce(nonce);
            candidateHash = b.calculateBlockHash();
        }
        b.setNonce(nonce);
        b.setHash(candidateHash);
        // step 4: validate & add the block
        if(validate(b)){
            chain.add(b); // congrats, you passed!
            // step 5: send it to all our peers
            for(Socket s:peers){
                try{
                    ObjectOutputStream output = outputStreams.get(s);
                    output.writeObject(b);
                    output.reset();
                }catch(Exception e){
                    //TODO: clean up
                    e.printStackTrace();
                }
            }
        }
    }

    public boolean validate(Block candidate){
        // first validate the chain
        String prefixZeros = new String(new char[N]).replace('\0','0');
        for(int i=0;i<chain.size();i++){
            Block b = chain.get(i);
            if(!b.calculateBlockHash().equals(b.getHash())) return false;
            if(i==0){
                if(!b.getPrevHash().equals("")) return false;
            }else{
                Block previous = chain.get(i-1); // we know there are at least 2 block in the chain => there must be a previous block from here
                if(!b.getPrevHash().equals(previous.getHash())
                    || !b.getHash().substring(0,N).equals(prefixZeros))
                        return false;
            }
        }
        // then validate the candidate
        if(!candidate.calculateBlockHash().equals(candidate.getHash())
            || !candidate.getPrevHash().equals(chain.getLast().getHash())
            || !candidate.getHash().substring(0,N).equals(prefixZeros)) 
                return false;
        return true;
    }

    public List<Block> getChain(){
        return chain;
    }
    public List<Socket> getPeers(){
        return peers;
    }
    public Map<Socket,ObjectInputStream> getInputStreams(){
        return inputStreams;
    }
    public Map<Socket,ObjectOutputStream> getOutputStreams(){
        return outputStreams;
    }


    private ArrayList<Block> determineConsensus(ArrayList<ArrayList<Block>> candidateChains){
        Map<String,ArrayList<Block>> stringRepresentations = new HashMap<String,ArrayList<Block>>();
        Map<String,Integer> histogram = new HashMap<String,Integer>();

        for(ArrayList<Block> candidate:candidateChains){
            String chainStr = "";
            for(Block b:candidate) chainStr += "\t"+b+"\n"; // use the toString representation of the chain for simplicity in comparing chains

            if(!stringRepresentations.containsKey(chainStr)) stringRepresentations.put(chainStr,candidate);
            if(!histogram.containsKey(chainStr))
                histogram.put(chainStr,1);
            else
                histogram.put(chainStr,histogram.get(chainStr)+1); // also count the frequency of each string representation to determine the majority vote
        }
        // now find the maximal value for histogram (that is, the most common string representation of the consensus chain)
        Map.Entry<String,Integer> maxEntry = null;
        for(Map.Entry<String,Integer> entry:histogram.entrySet()) // can't believe I haven't used the entryset of a map before
            if(maxEntry == null || entry.getValue().compareTo(maxEntry.getValue()) > 0) maxEntry = entry; // checking if compareTo > 0 will return the first maximal value, in the case that there are multiple consensus chains (if that happens, god help us)
        
        return stringRepresentations.get(maxEntry.getKey());
    }


    @Override
    public String toString() {
        String chainStr = "";
        for(Block b:chain) chainStr += "\t"+b+"\n";
        return "BCNode [chain=\n" + chainStr + "]";
    }

    
}
