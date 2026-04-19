package helloWorld;

import peersim.edsim.*;
import peersim.core.*;
import peersim.config.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class HelloWorld implements EDProtocol {

    // ------------------------------------------------------------------ config
    private final int    transportPid;
    private final int    mypid;
    private final String prefix;

    /** Maximum logical ID on the ring */
    private final long maxLogicalId;

    /** L — total leafset size. L/2 predecessors + L/2 successors. */
    private final int leafsetSize;

    // ------------------------------------------------------------------ state
    private HWTransport transport;

    /** Node's logical ID */
    private long myLogicalId;

    /** PeerSim network-array index of this node. */
    private int nodeIdx;

    /**
     * Leafset: up to L/2 predecessors + L/2 successors on the logical-ID ring.
     * Sorted by logical ID ascending for readability; use ringDistance() for
     * all proximity decisions.
     */
    private List<Node> leafset;

    // ---------------------------------------------------------------- lifecycle

    public HelloWorld(String prefix) {
        this.prefix        = prefix;
        this.transportPid  = Configuration.getPid(prefix + ".transport");
        this.mypid         = Configuration.getPid(prefix + ".myself");
        this.maxLogicalId  = Configuration.getLong("simulation.maxIDlogique", 1000);
        this.leafsetSize   = Configuration.getInt("simulation.leafsetSize", 4);
        this.leafset       = new ArrayList<>();
    }

    @Override
    public Object clone() {
        HelloWorld dolly = new HelloWorld(this.prefix);
        return dolly;
    }

    // -------------------------------------------------------- PeerSim wiring

    public void setTransportLayer(int networkIdx) {
        this.nodeIdx       = networkIdx;
        this.myLogicalId   = Network.get(networkIdx).getID();
        this.transport     = (HWTransport) Network.get(networkIdx).getProtocol(transportPid);
    }

    // ---------------------------------------------------------- join procedure

    public void join() {
        int networkSize = Network.size();
        if (networkSize <= 1) {
            System.out.println(this + ": alone in the network, can't join.");
            return;
        }

        int randomNodeIdx;
        do {
            randomNodeIdx = (int) CommonState.r.nextLong(networkSize);
        } while (randomNodeIdx == this.nodeIdx);

        Node randomNode = Network.get(randomNodeIdx);
        System.out.println(this + ": sending JOIN to " + nodeLabel(randomNode));
        send(new Message(Message.JOIN, getMyNode()), randomNode);
    }

    // ---------------------------------------------------------- event dispatch

    @Override
    public void processEvent(Node node, int pid, Object event) {
        Message msg = (Message) event;
        switch (msg.getType()) {
            case Message.SELF_JOIN:      join();                 break;
            case Message.HELLOWORLD:     handleHelloWorld(msg);  break;
            case Message.JOIN:           handleJoin(msg);        break;
            case Message.JOIN_REPLY:     handleJoinReply(msg);   break;
            case Message.UPDATE_LEAFSET: handleUpdate(msg);      break;
            default:
                System.out.println(this + ": unknown message type " + msg.getType());
        }
    }

    // ---------------------------------------------------------------- JOIN handler
    //
    // Routing
    private void handleJoin(Message msg) {
        Node   joiner      = msg.getSender();
        long   joinerLogId = joiner.getID();

        msg.getVisited().add(this.myLogicalId);

        List<Node> candidates = new ArrayList<>();
        candidates.add(getMyNode());
        candidates.addAll(this.leafset);

        Node bestNode = getMyNode();
        long bestDist = minRingDistance(myLogicalId, joinerLogId);

        for (Node n : leafset) {
            if (msg.getVisited().contains(n.getID())) continue;
            long d = minRingDistance(n.getID(), joinerLogId);
            if (d < bestDist) {
                bestDist = d;
                bestNode = n;
            }
        }

        if (bestNode.getID() == this.myLogicalId) {
            System.out.println(this + ": answering JOIN from " + nodeLabel(joiner));
            List<Node> joinerLeafset = buildLeafsetFor(joinerLogId, candidates);
            send(new Message(Message.JOIN_REPLY, getMyNode(), joinerLeafset), joiner);
        } else {
            System.out.println(this + ": forwarding JOIN from " + nodeLabel(joiner)
                    + " to " + nodeLabel(bestNode));
            send(msg, bestNode);
        }
    }

    // --------------------------------------- JOIN_REPLY handler (joiner side)

    private void handleJoinReply(Message msg) {
        this.leafset = new ArrayList<>(msg.getLeafset());
        sortLeafset();
        System.out.println(this + ": JOIN_REPLY received, leafset = " + leafsetToString());

        Message updateMsg = new Message(Message.UPDATE_LEAFSET, getMyNode(), null);
        for (Node neighbor : leafset) {
            send(updateMsg, neighbor);
        }
    }

    // --------------------------------------------- UPDATE_LEAFSET handler

    private void handleUpdate(Message msg) {
        Node  newNode  = msg.getSender();
        long  newLogId = newNode.getID();

        if (newLogId == this.myLogicalId) return;

        if (shouldBeInLeafset(newLogId)) {
            addToLeafset(newNode);
            trimLeafset();
            System.out.println(this + ": leafset updated after "
                    + nodeLabel(newNode) + " joined the leafset => " + leafsetToString());
        }
    }

    // ------------------------------------------------ HELLOWORLD (unchanged)

    private void handleHelloWorld(Message msg) {
        System.out.println(this + ": Received " + msg.getContent());
        Node successor = getRingSuccessor();
        if (successor != null) {
            send(msg, successor);
        }
    }

    // -------------------------------------------------- leafset mechanics

    private List<Node> buildLeafsetFor(long targetId, List<Node> candidates) {
        int half = leafsetSize / 2;

        List<Node> predecessors = new ArrayList<>();
        List<Node> successors   = new ArrayList<>();

        for (Node n : candidates) {
            long nId = n.getID();
            if (nId == targetId) continue;

            long distRight = ringDistance(targetId, nId);
            long distLeft  = ringDistance(nId, targetId);

            if (distRight <= maxLogicalId / 2) successors.add(n);
            else                               predecessors.add(n);
        }

        predecessors.sort(Comparator.comparingLong(n -> ringDistance(n.getID(), targetId)));
        successors.sort(  Comparator.comparingLong(n -> ringDistance(targetId, n.getID())));

        List<Node> result = new ArrayList<>();
        result.addAll(predecessors.subList(0, Math.min(half, predecessors.size())));
        result.addAll(successors.subList(0,   Math.min(half, successors.size())));
        result.sort(Comparator.comparingLong(Node::getID));
        return result;
    }

    private boolean shouldBeInLeafset(long candidateId) {
        if (candidateId == myLogicalId) return false;
        int half = leafsetSize / 2;

        int leftCount = 0, rightCount = 0;
        for (Node n : leafset) {
            long d = ringDistance(myLogicalId, n.getID());
            if (d <= maxLogicalId / 2) rightCount++;
            else                       leftCount++;
        }

        long distRight = ringDistance(myLogicalId, candidateId);
        long distLeft  = ringDistance(candidateId, myLogicalId);

        if (distRight <= maxLogicalId / 2 && rightCount < half) return true;
        if (distLeft  <  maxLogicalId / 2 && leftCount  < half) return true;

        if (distRight <= maxLogicalId / 2) {
            long furthest = leafset.stream()
                    .filter(n -> ringDistance(myLogicalId, n.getID()) <= maxLogicalId / 2)
                    .mapToLong(n -> ringDistance(myLogicalId, n.getID()))
                    .max().orElse(Long.MAX_VALUE);
            return distRight < furthest;
        } else {
            long furthest = leafset.stream()
                    .filter(n -> ringDistance(n.getID(), myLogicalId) < maxLogicalId / 2)
                    .mapToLong(n -> ringDistance(n.getID(), myLogicalId))
                    .max().orElse(Long.MAX_VALUE);
            return distLeft < furthest;
        }
    }

    /**
     * Adds a neighbor directly — used by Initializer
     */
    void addNeighbor(Node node) {
        addToLeafset(node);
    }

    private void addToLeafset(Node node) {
        for (Node n : leafset) {
            if (n.getID() == node.getID()) return;
        }
        leafset.add(node);
        sortLeafset();
    }


    void trimLeafset() {
        int half = leafsetSize / 2;

        List<Node> left  = new ArrayList<>();
        List<Node> right = new ArrayList<>();

        for (Node n : leafset) {
            long d = ringDistance(myLogicalId, n.getID());
            if (d <= maxLogicalId / 2) right.add(n);
            else                       left.add(n);
        }

        right.sort(Comparator.comparingLong(n -> ringDistance(myLogicalId, n.getID())));
        left.sort( Comparator.comparingLong(n -> ringDistance(n.getID(), myLogicalId)));

        if (right.size() > half) right = right.subList(0, half);
        if (left.size()  > half) left  = left.subList(0,  half);

        leafset = new ArrayList<>();
        leafset.addAll(left);
        leafset.addAll(right);
        sortLeafset();
    }

    // -------------------------------------------------- ring arithmetic

    private long ringDistance(long from, long to) {
        return Math.floorMod(to - from, maxLogicalId);
    }

    private long minRingDistance(long a, long b) {
        long cw  = ringDistance(a, b);
        long ccw = ringDistance(b, a);
        return Math.min(cw, ccw);
    }

    // -------------------------------------------------- helpers

    private Node getRingSuccessor() {
        Node best = null;
        long bestDist = Long.MAX_VALUE;
        for (Node n : leafset) {
            long d = ringDistance(myLogicalId, n.getID());
            if (d > 0 && d < bestDist) { bestDist = d; best = n; }
        }
        return best;
    }

    public void send(Message msg, Node dest) {
        this.transport.send(getMyNode(), dest, msg, this.mypid);
    }

    private Node getMyNode() { return Network.get(this.nodeIdx); }

    private void sortLeafset() {
        leafset.sort(Comparator.comparingLong(Node::getID));
    }

    public List<Node>  getLeafset()   { return leafset; }
    public long        getLogicalId() { return myLogicalId; }

    private String nodeLabel(Node n) { return "Node(id=" + n.getID() + ")"; }

    String leafsetToString() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < leafset.size(); i++) {
            sb.append("id=").append(leafset.get(i).getID());
            if (i < leafset.size() - 1) sb.append(", ");
        }
        return sb.append("]").toString();
    }

    @Override
    public String toString() { return "Node(id=" + myLogicalId + ")"; }
}