package DHT;

import peersim.edsim.*;
import peersim.core.*;
import peersim.config.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class DHTNode implements EDProtocol {

    // ------------------------------------------------------------------ config
    private final int    transportPid;
    private final int    mypid;
    private final String prefix;

    private long maxLogicalId;
    private int  leafsetSize;
    private int  maxFurthestNodes;

    // ------------------------------------------------------------------ state
    private Transport  transport;
    private long       myLogicalId;
    private int        nodeIdx;
    private List<Node> leafset;
    private List<Node> furthestNodes;

    // ---------------------------------------------------------------- lifecycle

    public DHTNode(String prefix) {
        this.prefix       = prefix;
        this.transportPid = Configuration.getPid(prefix + ".transport");
        this.mypid        = Configuration.getPid(prefix + ".myself");
        this.maxLogicalId     = readLong("simulation.maxIDlogique",  1000);
        this.leafsetSize      = readInt ("simulation.leafsetSize",   4);
        this.maxFurthestNodes = readInt ("simulation.maxNeighbours", 4);
        this.leafset          = new ArrayList<>();
        this.furthestNodes    = new ArrayList<>();
    }

    @Override
    public Object clone() { return new DHTNode(this.prefix); }

    // -------------------------------------------------------- PeerSim wiring

    public void setTransportLayer(int networkIdx) {
        this.maxLogicalId     = readLong("simulation.maxIDlogique",  1000);
        this.leafsetSize      = readInt ("simulation.leafsetSize",   4);
        this.maxFurthestNodes = readInt ("simulation.maxNeighbours", 4);
        this.nodeIdx          = networkIdx;
        this.transport        = (Transport) Network.get(networkIdx).getProtocol(transportPid);
        this.leafset          = new ArrayList<>();
        this.furthestNodes    = new ArrayList<>();
    }

    public void setLogicalId(long id) {
        this.myLogicalId = id;
    }

    // Get real logical ID
    private long getLogId(Node n) {
        return ((DHTNode) n.getProtocol(this.mypid)).getLogicalId();
    }

    // ---------------------------------------------------------- join procedure

    public void join() {
        int networkSize = Network.size();
        if (networkSize <= 1) return;

        int randomNodeIdx;
        do {
            randomNodeIdx = (int) CommonState.r.nextLong(networkSize);
        } while (randomNodeIdx == this.nodeIdx);

        Node randomNode = Network.get(randomNodeIdx);
        send(new Message(Message.JOIN, getMyNode()), randomNode);
    }

    // ---------------------------------------------------------- event dispatch

    @Override
    public void processEvent(Node node, int pid, Object event) {
        Message msg = (Message) event;

        if (msg.getSender() != null) {
            considerForFurthestNodes(msg.getSender());
        }

        switch (msg.getType()) {
            case Message.SELF_JOIN:      join();                 break;
            case Message.MESSAGE:        handleDHTNode(msg);     break;
            case Message.JOIN:           handleJoin(msg);        break;
            case Message.JOIN_REPLY:     handleJoinReply(msg);   break;
            case Message.UPDATE_LEAFSET: handleUpdate(msg);      break;
        }
    }

    // ---------------------------------------------------------------- JOIN handler

    private void handleJoin(Message msg) {
        Node joiner      = msg.getSender();
        long joinerLogId = getLogId(joiner); // FIXED

        msg.getPath().add(getMyNode());

        List<Node> candidates = new ArrayList<>();
        candidates.add(getMyNode());
        for (Node n : leafset)       { if (getLogId(n) != joinerLogId) candidates.add(n); } // FIXED
        for (Node n : furthestNodes) { if (getLogId(n) != joinerLogId) candidates.add(n); } // FIXED

        Node bestNode = getMyNode();
        long bestDist = minRingDistance(myLogicalId, joinerLogId);

        for (Node n : candidates) {
            if (getLogId(n) == myLogicalId) continue; // FIXED

            if (pathContainsId(msg.getPath(), getLogId(n))) continue; // FIXED

            long d = minRingDistance(getLogId(n), joinerLogId); // FIXED
            if (d < bestDist) {
                bestDist = d;
                bestNode = n;
            }
        }

        if (getLogId(bestNode) == this.myLogicalId) { // FIXED
            Benchmark.joinHopCounts.put(joinerLogId, msg.getPath().size());

            List<Node> richCandidates = new ArrayList<>(candidates);
            for (Node p : msg.getPath()) {
                if (getLogId(p) != joinerLogId) richCandidates.add(p); // FIXED
            }
            List<Node> joinerLeafset = buildLeafsetFor(joinerLogId, richCandidates);
            send(new Message(Message.JOIN_REPLY, getMyNode(), joinerLeafset), joiner);
        } else {
            send(msg, bestNode);
        }
    }

    private boolean pathContainsId(List<Node> path, long id) {
        for (Node p : path) {
            if (getLogId(p) == id) return true; // FIXED
        }
        return false;
    }

    // --------------------------------------- JOIN_REPLY handler

    private void handleJoinReply(Message msg) {
        this.leafset = new ArrayList<>(msg.getLeafset());
        sortLeafset();

        Message updateMsg = new Message(Message.UPDATE_LEAFSET, getMyNode(), null);
        for (Node neighbor : leafset) {
            send(updateMsg, neighbor);
        }
    }

    // --------------------------------------------- UPDATE_LEAFSET handler

    private void handleUpdate(Message msg) {
        Node newNode  = msg.getSender();
        long newLogId = getLogId(newNode); // FIXED

        if (newLogId == this.myLogicalId) return;

        if (shouldBeInLeafset(newLogId)) {
            addToLeafset(newNode);
            trimLeafset();
        }
    }

    // ------------------------------------------------ MESSAGE

    private void handleDHTNode(Message msg) {
        Node successor = getRingSuccessor();
        if (successor != null) {
            send(msg, successor);
        }
    }

    // -------------------------------------------------- long links

    private void considerForFurthestNodes(Node candidate) {
        long candId = getLogId(candidate); // FIXED
        if (candId == myLogicalId) return;

        for (Node n : leafset)       { if (getLogId(n) == candId) return; } // FIXED
        for (Node n : furthestNodes) { if (getLogId(n) == candId) return; } // FIXED

        if (furthestNodes.size() < maxFurthestNodes) {
            furthestNodes.add(candidate);
        } else {
            long candDist = minRingDistance(myLogicalId, candId);
            Node closestFar     = null;
            long closestFarDist = Long.MAX_VALUE;
            for (Node n : furthestNodes) {
                long d = minRingDistance(myLogicalId, getLogId(n)); // FIXED
                if (d < closestFarDist) { closestFarDist = d; closestFar = n; }
            }
            if (candDist > closestFarDist) {
                furthestNodes.remove(closestFar);
                furthestNodes.add(candidate);
            } else {
                return;
            }
        }

        furthestNodes.sort(Comparator.comparingLong(
                (Node n) -> minRingDistance(myLogicalId, getLogId(n))).reversed()); // FIXED
    }

    // -------------------------------------------------- leafset mechanics

    private List<Node> buildLeafsetFor(long targetId, List<Node> candidates) {
        int leftHalf = leafsetSize / 2;
        int rightHalf = leafsetSize - leftHalf;

        List<Node> deduped = new ArrayList<>();
        for (Node n : candidates) {
            long nId = getLogId(n); // FIXED
            if (nId == targetId) continue;
            boolean seen = false;
            for (Node d : deduped) { if (getLogId(d) == nId) { seen = true; break; } } // FIXED
            if (!seen) deduped.add(n);
        }

        deduped.sort(Comparator.comparingLong(n -> minRingDistance(getLogId(n), targetId))); // FIXED

        List<Node> predecessors = new ArrayList<>();
        List<Node> successors   = new ArrayList<>();
        List<Node> overflow     = new ArrayList<>();

        for (Node n : deduped) {
            long cw = ringDistance(targetId, getLogId(n)); // FIXED
            boolean isSuccessor = cw <= maxLogicalId / 2;
            if (isSuccessor && successors.size() < rightHalf) {
                successors.add(n);
            } else if (!isSuccessor && predecessors.size() < leftHalf) {
                predecessors.add(n);
            } else {
                overflow.add(n);
            }
        }

        for (Node n : overflow) {
            if (predecessors.size() < leftHalf)      predecessors.add(n);
            else if (successors.size() < rightHalf)   successors.add(n);
            else                                 break;
        }

        List<Node> result = new ArrayList<>();
        result.addAll(predecessors);
        result.addAll(successors);
        result.sort(Comparator.comparingLong(n -> getLogId(n))); // FIXED
        return result;
    }

    private boolean shouldBeInLeafset(long candidateId) {
        if (candidateId == myLogicalId) return false;

        int leftHalf = leafsetSize / 2;
        int rightHalf = leafsetSize - leftHalf;

        int leftCount = 0, rightCount = 0;
        for (Node n : leafset) {
            long d = ringDistance(myLogicalId, getLogId(n)); // FIXED
            if (d <= maxLogicalId / 2) rightCount++;
            else                       leftCount++;
        }

        long distRight = ringDistance(myLogicalId, candidateId);
        long distLeft  = ringDistance(candidateId, myLogicalId);

        if (distRight <= maxLogicalId / 2 && rightCount < rightHalf) return true;
        if (distLeft  <  maxLogicalId / 2 && leftCount  < leftHalf) return true;

        if (distRight <= maxLogicalId / 2) {
            long furthest = leafset.stream()
                    .filter(n -> ringDistance(myLogicalId, getLogId(n)) <= maxLogicalId / 2) // FIXED
                    .mapToLong(n -> ringDistance(myLogicalId, getLogId(n))) // FIXED
                    .max().orElse(Long.MAX_VALUE);
            return distRight < furthest;
        } else {
            long furthest = leafset.stream()
                    .filter(n -> ringDistance(getLogId(n), myLogicalId) < maxLogicalId / 2) // FIXED
                    .mapToLong(n -> ringDistance(getLogId(n), myLogicalId)) // FIXED
                    .max().orElse(Long.MAX_VALUE);
            return distLeft < furthest;
        }
    }

    void addNeighbor(Node node) { addToLeafset(node); }

    private void addToLeafset(Node node) {
        for (Node n : leafset) { if (getLogId(n) == getLogId(node)) return; } // FIXED
        leafset.add(node);
        sortLeafset();
    }

    void trimLeafset() {
        int leftHalf = leafsetSize / 2;
        int rightHalf = leafsetSize - leftHalf;

        List<Node> left  = new ArrayList<>();
        List<Node> right = new ArrayList<>();

        for (Node n : leafset) {
            long d = ringDistance(myLogicalId, getLogId(n)); // FIXED
            if (d <= maxLogicalId / 2) right.add(n);
            else                       left.add(n);
        }

        right.sort(Comparator.comparingLong(n -> ringDistance(myLogicalId, getLogId(n)))); // FIXED
        left.sort( Comparator.comparingLong(n -> ringDistance(getLogId(n), myLogicalId))); // FIXED

        if (right.size() > rightHalf) right = right.subList(0, rightHalf);
        if (left.size()  > leftHalf)  left  = left.subList(0,  leftHalf);

        leafset = new ArrayList<>();
        leafset.addAll(left);
        leafset.addAll(right);
        sortLeafset();
    }

    // -------------------------------------------------- compute ring distance

    private long ringDistance(long from, long to) {
        return Math.floorMod(to - from, maxLogicalId);
    }

    private long minRingDistance(long a, long b) {
        return Math.min(ringDistance(a, b), ringDistance(b, a));
    }

    // -------------------------------------------------- helpers

    private Node getRingSuccessor() {
        Node best = null;
        long bestDist = Long.MAX_VALUE;
        for (Node n : leafset) {
            long d = ringDistance(myLogicalId, getLogId(n)); // FIXED
            if (d > 0 && d < bestDist) { bestDist = d; best = n; }
        }
        return best;
    }

    private static long readLong(String key, long def) {
        String v = System.getProperty(key);
        if (v != null) return Long.parseLong(v);
        return Configuration.getLong(key, def);
    }

    private static int readInt(String key, int def) {
        String v = System.getProperty(key);
        if (v != null) return Integer.parseInt(v);
        return Configuration.getInt(key, def);
    }

    public void send(Message msg, Node dest) {
        this.transport.send(getMyNode(), dest, msg, this.mypid);
    }

    private Node getMyNode() { return Network.get(this.nodeIdx); }

    private void sortLeafset() {
        leafset.sort(Comparator.comparingLong(n -> getLogId(n))); // FIXED
    }

    public List<Node> getLeafset()       { return leafset; }
    public List<Node> getFurthestNodes() { return furthestNodes; }
    public long       getLogicalId()     { return myLogicalId; }

    private String nodeLabel(Node n) { return "Node(id=" + getLogId(n) + ")"; } // FIXED

    String leafsetToString() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < leafset.size(); i++) {
            sb.append("id=").append(getLogId(leafset.get(i))); // FIXED
            if (i < leafset.size() - 1) sb.append(", ");
        }
        return sb.append("]").toString();
    }

    private String furthestNodesToString() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < furthestNodes.size(); i++) {
            Node n = furthestNodes.get(i);
            sb.append("id=").append(getLogId(n)) // FIXED
                    .append("(d=").append(minRingDistance(myLogicalId, getLogId(n))).append(")"); // FIXED
            if (i < furthestNodes.size() - 1) sb.append(", ");
        }
        return sb.append("]").toString();
    }

    @Override
    public String toString() { return "Node(id=" + myLogicalId + ")"; }
}