package DHT;

import peersim.config.*;
import peersim.core.*;
import peersim.edsim.*;

public class Transport implements Protocol {

    private final long min;
    private final long range;

    public Transport(String prefix) {
        System.out.println("Transport Layer Enabled");
        min = Configuration.getInt(prefix + ".mindelay");
        long max = Configuration.getInt(prefix + ".maxdelay");
        if (max < min) {
            System.out.println("The maximum latency cannot be smaller than the minimum latency");
            System.exit(1);
        }
        range = max - min + 1;
    }

    public Object clone() { return this; }

    public void send(Node src, Node dest, Object msg, int pid) {
        long delay = getLatency(src, dest);
        EDSimulator.add(delay, msg, dest, pid);
    }

    public long getLatency(Node src, Node dest) {
        return (range == 1 ? min : min + CommonState.r.nextLong(range));
    }
}