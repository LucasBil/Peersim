package helloWorld;

import peersim.core.Node;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Message {

    public final static int HELLOWORLD     = 0;
    public final static int JOIN           = 1;
    public final static int JOIN_REPLY     = 2;
    public final static int UPDATE_LEAFSET = 3;
    public final static int SELF_JOIN      = 4;

    private final int   type;
    private String      content;
    private Node        sender;
    private List<Node>  leafset;

    // Tracks which nodes have already forwarded this JOIN, to prevent loops.
    // Carried along as the message is routed hop by hop.
    private Set<Long>   visited;

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
}