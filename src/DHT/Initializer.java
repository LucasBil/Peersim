package DHT;

import DHT.DHTNode;
import DHT.Message;
import peersim.config.Configuration;
import peersim.core.Control;
import peersim.core.Network;
import peersim.core.Node;

/*
  Module d'initialisation de helloWorld: 
  Fonctionnement:
    pour chaque noeud, le module fait le lien entre la couche transport et la couche applicative
    ensuite, il fait envoyer au noeud 0 un message "Hello" a tous les autres noeuds
 */
public class Initializer implements Control {
    
    private int DHTPid;

    public Initializer(String prefix) {
	//recuperation du pid de la couche applicative
	this.DHTPid = Configuration.getPid(prefix + ".helloWorldProtocolPid");
    }

    public boolean execute() {
	int nodeNb;
	DHTNode emitter, current;
	Node dest;
	Message helloMsg;

	//recuperation de la taille du reseau
	nodeNb = Network.size();
	//creation du message
	helloMsg = new Message(Message.HELLOWORLD,"Hello!!");
	//joinMsg = new Message(Message.JOIN,"let me in!!");
	if (nodeNb < 1) {
	    System.err.println("Network size is not positive");
	    System.exit(1);
	}

	//recuperation de la couche applicative de l'emetteur (le noeud 0)
	emitter = (DHTNode) Network.get(0).getProtocol(this.DHTPid);
	emitter.setTransportLayer(0);
	int maxNeighbours = Configuration.getInt( "simulation.maxNeighbours");
	int maxNeighboursLeft = maxNeighbours/2;
	int maxNeighboursRight = maxNeighbours - maxNeighboursLeft;
	int networkSize = Network.size();
	//pour chaque noeud, on fait le lien entre la couche applicative et la couche transport
	for (int i = 0; i < nodeNb; i++) {
		dest = Network.get(i);
		current = (DHTNode) dest.getProtocol(this.DHTPid);
		current.setTransportLayer(i);
	}


	for (int i = 0; i < nodeNb; i++) {
	    dest = Network.get(i);
	    current = (DHTNode) dest.getProtocol(this.DHTPid);
	    current.setTransportLayer(i);
		// Voisins de gauche
		for (int j = 1; j <= maxNeighboursLeft; j++){
			int index = (networkSize + i - j) % networkSize;
			Node desti =Network.get(index);
			DHTNode nodecu = (DHTNode) desti.getProtocol(this.DHTPid);
			current.addNeighbours(nodecu);
		}
		// Voisins de droite
		for (int j = 1; j <= maxNeighboursRight; j++){
			int index = (i + j) % networkSize;
			Node desti = Network.get(index);
			DHTNode nodecu = (DHTNode) desti.getProtocol(this.DHTPid);
			current.addNeighbours(nodecu);
		}
		System.out.println(current + " : " + current.getNeighbours());
	}

	System.out.println("Initialization completed");
	return false;
    }
}