package DHT;

import peersim.config.Configuration;
import peersim.core.CommonState;
import peersim.core.Node;
import peersim.core.Protocol;
import peersim.edsim.EDSimulator;

public class Transport implements Protocol {

    //variables pour calculer la latence entre les noeuds
    private final long min;

    private final long range;


    public Transport(String prefix) {
	System.out.println("Transport Layer Enabled");
	//recuperation des valeurs extremes de latence depuis le fichier de configuration
	min = Configuration.getInt(prefix + ".mindelay");
	long max = Configuration.getInt(prefix + ".maxdelay");
	if (max < min) {
	    System.out.println("The maximum latency cannot be smaller than the minimum latency");
	    System.exit(1);
	}
	range = max-min+1;
    }
    
    
    public Object clone() {
	return this;
    }
    

    //envoi d'un message: il suffit de l'ajouter a la file d'evenements
    public void send(peersim.core.Node src, peersim.core.Node dest, Object msg, int pid) {
	long delay = getLatency(src,dest);
	EDSimulator.add(delay, msg, dest, pid);
    }
    
    
    //latence random entre la borne min et la borne max
    public long getLatency(peersim.core.Node src, Node dest) {
	return (range==1?min:min + CommonState.r.nextLong(range));
    }


}

