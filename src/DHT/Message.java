package DHT;

import java.util.ArrayList;
import java.util.UUID;

public class Message {

    public final static int HELLOWORLD = 0;
    public final static int JOIN = 1;
    public final static int RING = 2;
    public final static int PING = 3;

    private UUID uuid;
    private int type;
    private String content;
    private ArrayList<Integer> tracks;

    Message(int type) {
        this(type, "No content");
    }

    Message(int type, String content) {
        this.type = type;
        this.content = content;
        this.tracks = new ArrayList<>();
        this.uuid = UUID.randomUUID();
    }

    public ArrayList<Integer> getTracks() {
        return this.tracks;
    }

    public void addTracks(int nodeIdDHT) {
        tracks.add(nodeIdDHT);
    }

    public String getContent() {
	return this.content;
    }

    public int getType() {
	return this.type;
    }

    public UUID getUUID() { return this.uuid; }
    
}