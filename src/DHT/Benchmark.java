package DHT;

import peersim.core.*;
import peersim.config.*;

import java.util.*;

/**
 * Benchmark — drives parameter sweeps across PeerSim experiments and
 * measures DHT ring quality at the end of each run.
 */
public class Benchmark implements peersim.core.Control {

    // ------------------------------------------------------------------ sweep tables

    private static final int[] LEAFSET_SIZES   = {2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 15, 20, 24, 30, 40, 50, 64, 128, 256};
    private static final int[] MAX_NEIGHBOURS  = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 16, 20, 24, 32, 48, 64, 128, 256};
    private static final int[] MAX_IDS         = {2000, 2500, 3000, 3500, 4000, 4500, 5000, 7500, 10000, 15000, 20000, 50000, 100000, 250000};
    private static final int[] BOOTSTRAP_SIZES = {1, 2, 3, 5, 10, 15, 20, 30, 50, 75, 100, 150, 200, 300, 500};

    // Total number of trials per config (takes average of each N trials for statistical stability)
    private static final int TRIALS_PER_CONFIG = 10;

    // Total experiments, this has to match the experiments value in the config file
    public static final int TOTAL_EXPERIMENTS = (LEAFSET_SIZES.length + MAX_NEIGHBOURS.length + MAX_IDS.length + BOOTSTRAP_SIZES.length) * TRIALS_PER_CONFIG;

    private static int currentExpIndex = 0;

    // ------------------------------------------------------------------ accumulator for averages

    private static final List<ExperimentResult> results = new ArrayList<>();
    private static Metrics accumulator = new Metrics();

    // ------------------------------------------------------------------ hop tracking

    public static final Map<Long, Integer> joinHopCounts = new HashMap<>();

    // ------------------------------------------------------------------ DHTPid

    private final int DHTPid;

    public Benchmark(String prefix) {
        this.DHTPid = Configuration.getPid(prefix + ".DHTProtocolPid");
    }

    // ------------------------------------------------------------------ parameter injection

    public static void applyExperimentParameters(int experiment) {
        currentExpIndex = experiment;
        int configIndex = experiment / TRIALS_PER_CONFIG;

        int leafsetSize, maxNeighbours, maxIDlogique, bootstrapSize;

        if (configIndex < LEAFSET_SIZES.length) {
            leafsetSize   = LEAFSET_SIZES[configIndex];
            maxNeighbours = 6;
            maxIDlogique  = 2000;
            bootstrapSize = 50;
        } else if (configIndex < LEAFSET_SIZES.length + MAX_NEIGHBOURS.length) {
            int i = configIndex - LEAFSET_SIZES.length;
            leafsetSize   = 4;
            maxNeighbours = MAX_NEIGHBOURS[i];
            maxIDlogique  = 2000;
            bootstrapSize = 50;
        } else if (configIndex < LEAFSET_SIZES.length + MAX_NEIGHBOURS.length + MAX_IDS.length) {
            int i = configIndex - LEAFSET_SIZES.length - MAX_NEIGHBOURS.length;
            leafsetSize   = 6;
            maxNeighbours = 4;
            maxIDlogique  = MAX_IDS[i];
            bootstrapSize = 50;
        } else {
            int i = configIndex - LEAFSET_SIZES.length - MAX_NEIGHBOURS.length - MAX_IDS.length;
            leafsetSize   = 6;
            maxNeighbours = 4;
            maxIDlogique  = 2000;
            bootstrapSize = BOOTSTRAP_SIZES[i];
        }

        System.setProperty("simulation.leafsetSize",   String.valueOf(leafsetSize));
        System.setProperty("simulation.maxNeighbours", String.valueOf(maxNeighbours));
        System.setProperty("simulation.maxIDlogique",  String.valueOf(maxIDlogique));
        System.setProperty("simulation.bootstrapSize", String.valueOf(bootstrapSize));

        System.out.println(">>> Run " + experiment + " (Config " + configIndex + ") | Trial " + (experiment % TRIALS_PER_CONFIG + 1));
    }

    // ------------------------------------------------------------------ control execution

    @Override
    public boolean execute() {
        int exp         = currentExpIndex;
        int configIndex = exp / TRIALS_PER_CONFIG;
        int nodeNb      = Network.size();

        // Retrieve current parameters for metrics calculation
        int leafsetSize   = Integer.parseInt(System.getProperty("simulation.leafsetSize", "4"));
        int maxNeighbours = Integer.parseInt(System.getProperty("simulation.maxNeighbours", "4"));
        long maxIDlogique = Long.parseLong(System.getProperty("simulation.maxIDlogique", "1000"));
        int bootstrapSize = Integer.parseInt(System.getProperty("simulation.bootstrapSize", "30"));

        // 1. Compute metrics for this specific run
        Metrics currentRun = computeMetrics(nodeNb, leafsetSize, maxIDlogique, maxNeighbours);

        // 2. Accumulate values for averaging
        accumulator.completenessPercent += currentRun.completenessPercent;
        accumulator.correctnessPercent  += currentRun.correctnessPercent;
        accumulator.longLinkCoverage    += currentRun.longLinkCoverage;
        accumulator.avgHops             += currentRun.avgHops;

        // 3. If this is the LAST trial of the current configuration, finalize the average
        if ((exp + 1) % TRIALS_PER_CONFIG == 0) {
            accumulator.completenessPercent /= TRIALS_PER_CONFIG;
            accumulator.correctnessPercent  /= TRIALS_PER_CONFIG;
            accumulator.longLinkCoverage    /= TRIALS_PER_CONFIG;
            accumulator.avgHops             /= TRIALS_PER_CONFIG;

            String sweepName  = getSweepName(configIndex);
            String paramLabel = getParamLabel(leafsetSize, maxNeighbours, (int)maxIDlogique, bootstrapSize);

            results.add(new ExperimentResult(sweepName, paramLabel, accumulator));

            // Reset for next config
            accumulator = new Metrics();
        }

        joinHopCounts.clear();

        // Print debug and full table on the very last experiment run
        if (exp == TOTAL_EXPERIMENTS - 1) {
            // Passed the parameters down so they can be printed in the header
            printDebugNodes(nodeNb, leafsetSize, maxNeighbours, maxIDlogique, bootstrapSize);
            printResultsTable();
        }

        return false;
    }

    // ------------------------------------------------------------------ metrics logic

    private Metrics computeMetrics(int nodeNb, int leafsetSize, long maxIDlogique, int maxNeighbours) {
        Metrics m = new Metrics();

        long[] sortedIds = new long[nodeNb];
        DHTNode[] nodes  = new DHTNode[nodeNb];
        for (int i = 0; i < nodeNb; i++) {
            nodes[i]     = (DHTNode) Network.get(i).getProtocol(DHTPid);
            sortedIds[i] = nodes[i].getLogicalId();
        }
        Arrays.sort(sortedIds);

        int totalSlots = 0, correctSlots = 0, fullLS = 0, fullLL = 0;

        for (DHTNode node : nodes) {
            List<Node> ls = node.getLeafset();
            if (ls.size() >= leafsetSize) fullLS++;
            if (node.getFurthestNodes().size() >= maxNeighbours) fullLL++;

            // Pass the full leafsetSize so the asymmetric split can happen inside getTrueNeighbors
            Set<Long> trueNeighbors = getTrueNeighbors(node.getLogicalId(), sortedIds, leafsetSize);
            totalSlots += ls.size();
            for (Node n : ls) {
                long neighborLogicalId = ((DHTNode)n.getProtocol(DHTPid)).getLogicalId();
                if (trueNeighbors.contains(neighborLogicalId)) {
                    correctSlots++;
                }
            }
        }

        m.completenessPercent = 100.0 * fullLS / nodeNb;
        m.correctnessPercent  = totalSlots > 0 ? 100.0 * correctSlots / totalSlots : 0;
        m.longLinkCoverage    = 100.0 * fullLL / nodeNb;

        if (!joinHopCounts.isEmpty()) {
            double total = 0;
            for (int h : joinHopCounts.values()) total += h;
            m.avgHops = total / joinHopCounts.size();
        }

        return m;
    }

    private Set<Long> getTrueNeighbors(long myId, long[] sortedIds, int leafsetSize) {
        Set<Long> result = new HashSet<>();
        int n   = sortedIds.length;
        int pos = Arrays.binarySearch(sortedIds, myId);
        if (pos < 0) return result;

        // Apply Asymmetric Logic for accurate correctness benchmarking on odd leafsets
        int leftHalf = leafsetSize / 2;
        int rightHalf = leafsetSize - leftHalf;

        for (int i = 1; i <= rightHalf; i++) {
            result.add(sortedIds[(pos + i) % n]);
        }
        for (int i = 1; i <= leftHalf; i++) {
            result.add(sortedIds[Math.floorMod(pos - i, n)]);
        }
        return result;
    }

    // ------------------------------------------------------------------ output / debugging

    private void printDebugNodes(int nodeNb, int ls, int mn, long mid, int bs) {
        System.out.println("\n+" + "-".repeat(95) + "+");
        System.out.println("|                            DEBUG: 10 RANDOM NODES STATE                              |");
        System.out.printf("| Current Parameters: ls=%-3d | mn=%-3d | mid=%-6d | bs=%-3d                            |%n", ls, mn, mid, bs);
        System.out.println("+" + "-".repeat(95) + "+");

        // Pick 10 unique random indices
        int count = Math.min(10, nodeNb);
        Set<Integer> randomIndices = new HashSet<>();
        while (randomIndices.size() < count) {
            randomIndices.add(CommonState.r.nextInt(nodeNb));
        }

        for (int idx : randomIndices) {
            DHTNode node = (DHTNode) Network.get(idx).getProtocol(DHTPid);

            // Extract IDs for Leafset
            List<Long> lsIds = new ArrayList<>();
            for (Node n : node.getLeafset()) {
                lsIds.add(((DHTNode) n.getProtocol(DHTPid)).getLogicalId());
            }

            // Extract IDs for Long Links (Furthest Nodes)
            List<Long> llIds = new ArrayList<>();
            for (Node n : node.getFurthestNodes()) {
                llIds.add(((DHTNode) n.getProtocol(DHTPid)).getLogicalId());
            }

            System.out.printf("| Node: %-6d | Leafset: %-30s | LongLinks: %-23s |%n",
                    node.getLogicalId(), lsIds.toString(), llIds.toString());
        }
        System.out.println("+" + "-".repeat(95) + "+");
    }

    private void printResultsTable() {
        System.out.println("\n+" + "-".repeat(95) + "+");
        System.out.println("|                         BENCHMARK SUMMARY (AVERAGED OVER " + TRIALS_PER_CONFIG + " TRIALS)                        |");
        System.out.println("+" + "-".repeat(95) + "+");

        String[] sweeps = {"leafsetSize", "maxNeighbours", "maxIDlogique", "bootstrapSize"};
        for (String sweep : sweeps) {
            System.out.println("\n| Sweep: " + sweep);
            System.out.printf("| %-35s | %-12s | %-12s | %-11s | %-10s |%n",
                    "Parameters", "Completeness", "Correctness", "LongLinks", "AvgHops");
            System.out.println("|" + "-".repeat(95) + "|");
            for (ExperimentResult r : results) {
                if (!r.sweepName.equals(sweep)) continue;
                System.out.printf("| %-35s | %11.1f%% | %11.1f%% | %10.1f%% | %10.2f |%n",
                        r.paramLabel,
                        r.metrics.completenessPercent,
                        r.metrics.correctnessPercent,
                        r.metrics.longLinkCoverage,
                        r.metrics.avgHops);
            }
        }
        System.out.println("+" + "-".repeat(95) + "+\n");
    }

    private String getSweepName(int configIndex) {
        if (configIndex < LEAFSET_SIZES.length) return "leafsetSize";
        if (configIndex < LEAFSET_SIZES.length + MAX_NEIGHBOURS.length) return "maxNeighbours";
        if (configIndex < LEAFSET_SIZES.length + MAX_NEIGHBOURS.length + MAX_IDS.length) return "maxIDlogique";
        return "bootstrapSize";
    }

    private String getParamLabel(int ls, int mn, int mid, int bs) {
        // Formats all 4 parameters into a clean string layout
        return String.format("ls=%-3d mn=%-3d mid=%-6d bs=%-3d", ls, mn, mid, bs);
    }

    static class Metrics {
        double completenessPercent = 0, correctnessPercent = 0, longLinkCoverage = 0, avgHops = 0;
    }

    static class ExperimentResult {
        final String sweepName, paramLabel;
        final Metrics metrics;
        ExperimentResult(String s, String p, Metrics m) {
            sweepName = s; paramLabel = p; metrics = m;
        }
    }
}