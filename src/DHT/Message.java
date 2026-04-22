package DHT;

import peersim.core.Node;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Message {

    public final static int MESSAGE     = 0;
    public final static int JOIN           = 1;
    public final static int JOIN_REPLY     = 2;
    public final static int UPDATE_LEAFSET = 3;
    public final static int SELF_JOIN      = 4;

    private final int   type;
    private String      content;
    private Node        sender;
    private List<Node>  leafset;

    // For JOIN routing: tracks visited node IDs to prevent loops
    private Set<Long>   visited;

    // For JOIN routing: accumulates every node the JOIN passes through.
    // When the answering node builds the joiner's leafset, it includes
    // all path nodes as candidates — they are guaranteed to be
    // geographically close to the joiner on the ring.
    private List<Node>  path;

    // HELLOWORLD
    public Message(int type, String content) {
        this.type    = type;
        this.content = content;
    }

    // JOIN / SELF_JOIN
    public Message(int type, Node sender) {
        this.type    = type;
        this.sender  = sender;
        this.visited = new HashSet<>();
        this.path    = new ArrayList<>();
    }

    // JOIN_REPLY / UPDATE_LEAFSET
    public Message(int type, Node sender, List<Node> leafset) {
        this.type    = type;
        this.sender  = sender;
        this.leafset = leafset;
    }

    public int        getType()    { return type;    }
    public String     getContent() { return content; }
    public Node       getSender()  { return sender;  }
    public List<Node> getLeafset() { return leafset; }
    public Set<Long>  getVisited() { return visited; }
    public List<Node> getPath()    { return path;    }
}