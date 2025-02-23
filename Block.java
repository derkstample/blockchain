import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;

public class Block implements Serializable{ // implement Serializable so we can use ObjectStreams
    private String data;
    private long timestamp;
    private int nonce;
    private String hash;
    private String prevHash;

    public Block(){
        data = "GenesisBlock";
        timestamp = new Date().getTime();
        nonce = 0;
        prevHash = "";
        hash = calculateBlockHash();
    }

    public Block(String data){
        this.data = data;
        timestamp = new Date().getTime();
        nonce = 0;
        prevHash = "";
        hash = calculateBlockHash();
    }

    public String calculateBlockHash(){
        String instanceVarData = data;
        instanceVarData += timestamp;
        instanceVarData += nonce;
        instanceVarData += prevHash;
        String hashStr = "";
        try{
            MessageDigest d = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = d.digest(instanceVarData.getBytes("UTF-8"));
            StringBuffer buffer = new StringBuffer();
            for(byte b:hashBytes)
                buffer.append(String.format("%02x", b));
            hashStr = buffer.toString();
        }catch(NoSuchAlgorithmException | UnsupportedEncodingException e){
            e.printStackTrace();
        }
        return hashStr;
    }

    public int getNonce() {
        return nonce;
    }

    public void setNonce(int nonce) {
        this.nonce = nonce;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public String getPrevHash() {
        return prevHash;
    }

    public void setPrevHash(String prevHash) {
        this.prevHash = prevHash;
    }

    @Override
    public String toString() {
        return "Block [data=" + data + ", timestamp=" + timestamp + ", nonce=" + nonce + ", hash=" + hash
                + ", prevHash=" + prevHash + "]";
    }
}