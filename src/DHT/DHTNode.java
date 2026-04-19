package DHT;

import DHT.Transport;
import DHT.DHTNode;
import DHT.Message;
import peersim.config.Configuration;
import peersim.core.Network;
import peersim.edsim.*;

import java.util.ArrayList;
import java.util.Comparator;

public class DHTNode implements EDProtocol {
    public static ArrayList<DHTNode> DHTNodes = new ArrayList<>(); // ATTENTION cheat !

    private String prefix; // prefixe de la couche (nom de la variable de protocole du fichier de config)
    private int transportPid;
    private Transport transport;

    private int mypid; // identifiant de la couche courante (la couche applicative)
    private int nodeId; // le numero de noeud
    private int nodeIdDHT; // le numero de noeud dans la DHT

    private ArrayList<DHTNode> neighbours = new ArrayList<>();

    public DHTNode(String prefix) {
        this.prefix = prefix;
        this.transportPid = Configuration.getPid(prefix + ".transport");
        this.mypid = Configuration.getPid(prefix + ".myself");
        this.transport = null;
    }

    public void processEvent(peersim.core.Node node, int pid, Object event ) {
        this.receive((Message)event);
    }
    public Object clone() { return new DHTNode(this.prefix); }

    //region GETTER/SETTER
    public void setTransportLayer(int nodeId) {
        this.nodeId = nodeId;
        this.transport = (Transport) Network.get(this.nodeId).getProtocol(this.transportPid);
    }

    public int getNodeId() { return this.nodeId; }

    public void setNodeIdDHT(int id){
        this.nodeIdDHT = id;
    }
    public int getNodeIdDHT(){
        return this.nodeIdDHT;
    }

    public ArrayList<DHTNode> getNeighbours() {
        return this.neighbours;
    }
    public void setNeighbours(ArrayList<DHTNode> neighbours) { this.neighbours = neighbours; }
    public void addNeighbours(DHTNode node){ this.neighbours.add(node); }
    public void removeNeighbours(DHTNode node) { this.neighbours.remove(node); }

    private peersim.core.Node getMyNode() { return Network.get(this.nodeId); }
    //endregion

    public void send(Message msg, peersim.core.Node dest) {
        try {
            DHTNode dhtDest = DHTNode.DHTNodes.stream()
                    .filter(n -> n.getMyNode().equals(dest))
                    .findFirst()
                    .orElse(null);
            String log = String.format("SENDED:[%2d](%s) -> %s",
                    msg.getType(),
                    msg.getContent(),
                    dhtDest
            );
            Logger.getInstance().log(this, log, Logger.INFO);
        } catch (Exception e) { System.out.println("Log fail !");}
        this.transport.send(getMyNode(), dest, msg, this.mypid);
    }

    private void receive(Message msg) {
        String log = String.format("RECEIVED:[%2d](%s)",msg.getType(), msg.getContent());
        Logger.getInstance().log(this, log, Logger.INFO);
        switch (msg.getType()) {
            case Message.HELLOWORLD -> { System.out.println(msg.getContent()); }
            case Message.RING -> { this.processMessageRing(msg); }
            case Message.JOIN -> { this.processMessageJoin(msg); }
            case Message.PING -> { this.processMessagePing(msg); }
            default -> { System.out.println("Message not allow !"); }
        }
    }

    private void processMessageRing(Message msg) {
        if (msg.getTracks().isEmpty() || msg.getTracks().getFirst() != this.nodeIdDHT) {
            msg.addTracks(this.nodeIdDHT);
            peersim.core.Node dest = this.getFirstRight().getMyNode();
            send(msg, dest);
        }
    }

    private void processMessagePing(Message msg) {
        int destIdDHT = Integer.parseInt(msg.getContent().trim());

        if (this.nodeIdDHT == destIdDHT) {
            System.out.println("PING");
            return;
        }

        DHTNode node = this.getNeighboor(destIdDHT);
        msg.addTracks(this.nodeIdDHT);
        if (node == null) {
            node = this.getLastRight();
        }
        peersim.core.Node dest = node.getMyNode();
        this.send(msg, dest);
    }

    private void processMessageJoin(Message msg) {
        int targetIdDHT = Integer.parseInt(msg.getContent().trim());
        int placement = this.getIdListNeightboursPlacement(targetIdDHT);
        if (placement == this.neighbours.size()) {
            // TODO: FORWARD
        }
    }

    private DHTNode getFirstRight() {
        neighbours.sort(Comparator.comparingInt(DHTNode::getNodeIdDHT));
        for (DHTNode node : neighbours) {
            if (node.getNodeIdDHT() > this.nodeIdDHT) {
                return node;
            }
        }
        return neighbours.isEmpty() ? null : neighbours.get(0);
    }

    private DHTNode getFirstLeft() {
        neighbours.sort(Comparator.comparingInt(DHTNode::getNodeIdDHT).reversed());
        for (DHTNode node : neighbours) {
            if (node.getNodeIdDHT() < this.nodeIdDHT) {
                return node;
            }
        }
        return neighbours.isEmpty() ? null : neighbours.get(0);
    }

    private int getIdListNeightboursPlacement() {
        return this.getIdListNeightboursPlacement(this.nodeIdDHT);
    }

    private int getIdListNeightboursPlacement(int nodeIdDHT) {
        for (int i = 0; i < this.neighbours.size(); i++) {
            if (nodeIdDHT < this.neighbours.get(i).getNodeIdDHT())
                return i;
        }
        return this.neighbours.size();
    }

    private DHTNode getLastLeft() {
        neighbours.sort(Comparator.comparingInt(DHTNode::getNodeIdDHT));
        int placement = this.getIdListNeightboursPlacement();
        int idLastLeft = (placement + (this.neighbours.size()/2))%this.neighbours.size();
        return this.neighbours.get(idLastLeft);
    }

    private DHTNode getLastRight() {
        neighbours.sort(Comparator.comparingInt(DHTNode::getNodeIdDHT));
        int placement = this.getIdListNeightboursPlacement();
        int idLastRight = (placement + (this.neighbours.size()/2) -1)%this.neighbours.size();
        return this.neighbours.get(idLastRight);
    }

    private DHTNode getNeighboor(int idDHT) {
        return this.neighbours.stream()
            .filter(n -> n.getNodeIdDHT() == idDHT)
            .findFirst()
            .orElse(null);
    }

    public String toString() {
        return String.format("dht:%2d-n:%2d", this.getNodeIdDHT(), this.getNodeId());
    }

}
