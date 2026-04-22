package DHT;

import peersim.core.*;
import peersim.config.*;
import peersim.edsim.EDSimulator;

public class Initializer implements peersim.core.Control {

	private static final int BOOTSTRAP_SIZE = 10;

	private final int DHTPid;

	public Initializer(String prefix) {
		this.DHTPid = Configuration.getPid(prefix + ".DHTProtocolPid");
	}

	public boolean execute() {
		int nodeNb = Network.size();
		if (nodeNb < 1) {
			System.err.println("Network size is not positive");
			System.exit(1);
		}

		// Step 1: wire transport layers for ALL nodes
		for (int i = 0; i < nodeNb; i++) {
			DHTNode node = (DHTNode) Network.get(i).getProtocol(DHTPid);
			node.setTransportLayer(i);
		}

		// Step 2: choose bootstrap nodes spread evenly across the network array
		int bootstrapCount = Math.min(BOOTSTRAP_SIZE, nodeNb);
		int[] bootstrapIndices = new int[bootstrapCount];
		for (int i = 0; i < bootstrapCount; i++) {
			bootstrapIndices[i] = (int) Math.round((double) i * (nodeNb - 1) / (bootstrapCount - 1));
		}

		// Build each bootstrap node's leafset from the other bootstrap nodes
		for (int i = 0; i < bootstrapCount; i++) {
			DHTNode node = (DHTNode) Network.get(bootstrapIndices[i]).getProtocol(DHTPid);
			for (int j = 0; j < bootstrapCount; j++) {
				if (i == j) continue;
				node.addNeighbor(Network.get(bootstrapIndices[j]));
			}
			node.trimLeafset();
			System.out.println("Bootstrap Node(id=" + Network.get(bootstrapIndices[i]).getID() + ")"
					+ " leafset = " + node.leafsetToString());
		}

		// Step 3: schedule SELF_JOIN for all non-bootstrap nodes, staggered by 1 time unit
		int scheduled = 0;
		for (int i = 0; i < nodeNb; i++) {
			boolean isBootstrap = false;
			for (int bi : bootstrapIndices) { if (bi == i) { isBootstrap = true; break; } }
			if (isBootstrap) continue;

			Node peerNode = Network.get(i);
			Message selfJoin = new Message(Message.SELF_JOIN, peerNode);
			EDSimulator.add(++scheduled, selfJoin, peerNode, DHTPid);
		}

		System.out.println("Initialization completed — "
				+ bootstrapCount + " nodes bootstrapped manually, "
				+ scheduled + " SELF_JOIN events scheduled.");
		return false;
	}
}