package DHT;

import peersim.core.*;
import peersim.config.*;
import peersim.edsim.EDSimulator;

public class Initializer implements peersim.core.Control {

	private static int experimentCounter = 0;

	private final int DHTPid;

	public Initializer(String prefix) {
		this.DHTPid = Configuration.getPid(prefix + ".DHTProtocolPid");
	}

	public boolean execute() {
		int experiment = experimentCounter++;
		Benchmark.applyExperimentParameters(experiment);

		int nodeNb = Network.size();
		if (nodeNb < 1) {
			System.err.println("Network size is not positive");
			System.exit(1);
		}

		// Dynamically read the bootstrap size injected by the Benchmark sweep
		int bootstrapSizeConfig = Integer.parseInt(System.getProperty("simulation.bootstrapSize", "30"));

		// Step 1: wire transport layers for ALL nodes
		for (int i = 0; i < nodeNb; i++) {
			DHTNode node = (DHTNode) Network.get(i).getProtocol(DHTPid);
			node.setTransportLayer(i);
		}

		// Step 2: choose bootstrap nodes spread evenly across the network array
		int bootstrapCount = Math.min(bootstrapSizeConfig, nodeNb);
		int[] bootstrapIndices = new int[bootstrapCount];
		for (int i = 0; i < bootstrapCount; i++) {
			bootstrapIndices[i] = (int) Math.round((double) i * (nodeNb - 1) / Math.max(1, (bootstrapCount - 1)));
		}

		// Build each bootstrap node's leafset from the other bootstrap nodes
		for (int i = 0; i < bootstrapCount; i++) {
			DHTNode node = (DHTNode) Network.get(bootstrapIndices[i]).getProtocol(DHTPid);
			for (int j = 0; j < bootstrapCount; j++) {
				if (i == j) continue;
				node.addNeighbor(Network.get(bootstrapIndices[j]));
			}
			node.trimLeafset();
			//System.out.println("Bootstrap Node(id=" + Network.get(bootstrapIndices[i]).getID() + ") + " leafset = " + node.leafsetToString());
		}

		// Step 3: schedule SELF_JOIN for all non-bootstrap nodes, staggered by 1 time unit
		int scheduledTime = 0;
		for (int i = 0; i < nodeNb; i++) {
			boolean isBootstrap = false;
			for (int bi : bootstrapIndices) { if (bi == i) { isBootstrap = true; break; } }
			if (isBootstrap) continue;

			Node peerNode = Network.get(i);
			Message selfJoin = new Message(Message.SELF_JOIN, peerNode);
			EDSimulator.add(++scheduledTime, selfJoin, peerNode, DHTPid);
		}

		// -------------------------------------------------------------------------
		// Step 4: STABILIZATION WAVES (New Events)
		// -------------------------------------------------------------------------
		// Schedule a second wave of SELF_JOINs for ALL nodes (including bootstraps).
		// We add a massive delay (e.g., nodeNb * 2) to ensure these stabilization
		// events only fire AFTER the chaotic initial join phase has completely settled.

		int stabilizationDelay = scheduledTime + (nodeNb * 2);

		for (int i = 0; i < nodeNb; i++) {
			Node peerNode = Network.get(i);
			Message stabilizeJoin = new Message(Message.SELF_JOIN, peerNode);
			// Stagger the stabilization events so they don't all fire on the exact same tick
			EDSimulator.add(stabilizationDelay + i, stabilizeJoin, peerNode, DHTPid);
		}

		//System.out.println("Initialization completed — " + bootstrapCount + " nodes bootstrapped manually, " + scheduledTime + " initial joins, " + nodeNb + " stabilization events scheduled.");
		return false;
	}
}