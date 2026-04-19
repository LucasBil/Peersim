package helloWorld;

import peersim.core.*;
import peersim.config.*;
import peersim.edsim.EDSimulator;

public class Initializer implements peersim.core.Control {

	private static final int randomNode_SIZE = 10;

	private final int helloWorldPid;

	public Initializer(String prefix) {
		this.helloWorldPid = Configuration.getPid(prefix + ".helloWorldProtocolPid");
	}

	public boolean execute() {
		int nodeNb = Network.size();
		if (nodeNb < 1) {
			System.err.println("Network size is not positive");
			System.exit(1);
		}

		// Step 1: wire transport layers for ALL nodes first
		for (int i = 0; i < nodeNb; i++) {
			HelloWorld node = (HelloWorld) Network.get(i).getProtocol(helloWorldPid);
			node.setTransportLayer(i);
		}

		// Step 2: manually build correct leafsets for the first randomNode_SIZE nodes.
		int randomNodeCount = Math.min(randomNode_SIZE, nodeNb);
		for (int i = 0; i < randomNodeCount; i++) {
			HelloWorld node = (HelloWorld) Network.get(i).getProtocol(helloWorldPid);
			for (int j = 0; j < randomNodeCount; j++) {
				if (i == j) continue;
				node.addNeighbor(Network.get(j));
			}
			node.trimLeafset();
			System.out.println("randomNode Node(id=" + Network.get(i).getID() + ")"
					+ " leafset = " + node.leafsetToString());
		}

		// Step 3: every remaining node schedules a SELF_JOIN into the ED queue
		for (int i = randomNodeCount; i < nodeNb; i++) {
			Node peerNode = Network.get(i);
			Message selfJoin = new Message(Message.SELF_JOIN, peerNode);
			EDSimulator.add(i - randomNodeCount + 1, selfJoin, peerNode, helloWorldPid);
		}

		System.out.println("Initialization completed — "
				+ randomNodeCount + " nodes added manually, "
				+ (nodeNb - randomNodeCount) + " SELF_JOIN events scheduled.");
		return false;
	}
}