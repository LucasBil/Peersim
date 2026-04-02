package DHT;

import DHT.Transport;
import DHT.DHTNode;
import DHT.Message;
import peersim.config.Configuration;
import peersim.core.Network;
import peersim.edsim.*;

import java.util.ArrayList;

public class DHTNode implements EDProtocol {

    //identifiant de la couche transport
    private int transportPid;

    //objet couche transport
    private Transport transport;

    //identifiant de la couche courante (la couche applicative)
    private int mypid;

    //le numero de noeud
    private int nodeId;

    //prefixe de la couche (nom de la variable de protocole du fichier de config)
    private String prefix;

    private ArrayList<DHTNode> neighbours = new ArrayList<>();

    public DHTNode(String prefix) {
        this.prefix = prefix;
        //initialisation des identifiants a partir du fichier de configuration
        this.transportPid = Configuration.getPid(prefix + ".transport");
        this.mypid = Configuration.getPid(prefix + ".myself");
        this.transport = null;
    }

    //methode appelee lorsqu'un message est recu par le protocole HelloWorld du noeud
    public void processEvent(peersim.core.Node node, int pid, Object event ) {
        this.receive((Message)event);
    }

    //methode necessaire pour la creation du reseau (qui se fait par clonage d'un prototype)
    public Object clone() {

        DHTNode dolly = new DHTNode(this.prefix);

        return dolly;
    }

    //liaison entre un objet de la couche applicative et un
    //objet de la couche transport situes sur le meme noeud
    public void setTransportLayer(int nodeId) {
        this.nodeId = nodeId;
        this.transport = (Transport) Network.get(this.nodeId).getProtocol(this.transportPid);
    }

    //envoi d'un message (l'envoi se fait via la couche transport)
    public void send(Message msg, peersim.core.Node dest) {
        this.transport.send(getMyNode(), dest, msg, this.mypid);
    }

    public ArrayList<DHTNode> getNeighbours() {
        return this.neighbours;
    }

    public void setNeighbours(ArrayList<DHTNode> neighbours) {
        this.neighbours = neighbours;
    }

    public void addNeighbours(DHTNode node){
        this.neighbours.add(node);
    }

    //affichage a la reception
    private void receive(Message msg) {
        System.out.println(this + ": Received " + msg.getContent() + "TypeMessage :" + msg.getType());

        if (this.nodeId + 1 < Network.size()){
            peersim.core.Node dest = Network.get(this.nodeId + 1);
            send(msg, dest);
        } else if ((this.nodeId)%Network.size() == 0) {
            peersim.core.Node dest = Network.get(0);
            send(msg, dest);
        }

    }

    //retourne le noeud courant
    private peersim.core.Node getMyNode() {
        return Network.get(this.nodeId);
    }

    public String toString() {
        return "Node "+ this.nodeId;
    }


}
