package DHT;

import DHT.DHTNode;
import DHT.Message;
import peersim.config.Configuration;
import peersim.core.Control;
import peersim.core.Network;
import peersim.core.Node;

import java.util.Random;

public class Initializer implements Control {
    
    private int DHTPid;

    public Initializer(String prefix) {
		this.DHTPid = Configuration.getPid(prefix + ".helloWorldProtocolPid");
    }

    public boolean execute() {
		int nodeNb = Network.size();
		if (nodeNb < 1) {
			System.err.println("Network size is not positive");
			System.exit(1);
		}

		DHTNode emitter, current;
		Node dest;
		//recuperation de la couche applicative de l'emetteur (le noeud 0)
		Node nodeEmitter = Network.get(0);
		emitter = (DHTNode) nodeEmitter.getProtocol(this.DHTPid);
		emitter.setTransportLayer(0);
		int maxNeighbours = Configuration.getInt( "simulation.maxNeighbours");
		int maxNeighboursLeft = maxNeighbours/2;
		int maxNeighboursRight = maxNeighbours - maxNeighboursLeft;
		int networkSize = Configuration.getInt( "simulation.network-init");
		int spaceInterval = Configuration.getInt("simulation.maxIDlogique")/(networkSize);
		//pour chaque noeud, on fait le lien entre la couche applicative et la couche transport
		for (int i = 0; i < nodeNb; i++) {
			dest = Network.get(i);
			current = (DHTNode) dest.getProtocol(this.DHTPid);
			current.setTransportLayer(i);
			current.setNodeIdDHT(i*spaceInterval);
		}

		for (int i = 0; i < networkSize; i++) {
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
			DHTNode.DHTNodes.add(current);
		}

		System.out.println("Initialization completed");

		//creation du message
		// Message msg = new Message(Message.RING);
		int destId = new Random().nextInt(DHTNode.DHTNodes.size());
		Message msg = new Message(
				Message.PING,
				String.format("%2d", DHTNode.DHTNodes.get(destId).getNodeIdDHT())
		);
		emitter.send(msg, nodeEmitter);

		return false;
    }
}