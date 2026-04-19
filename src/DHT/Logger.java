package DHT;

import peersim.core.CommonState;

public class Logger {
    private static Logger instance = new Logger();
    public static Logger getInstance() { return Logger.instance; }

    public static String ALERT = "ALERT";
    public static String WARNING = "WARNING";
    public static String INFO = "INFO";

    public Logger() {}

    public void log(String content, String level) {
        long time = CommonState.getTime();
        String log = String.format("[%s] | %2d | %s", level, time, content);
        System.out.println(log);
    }

    public void log(DHTNode node, String content, String level) {
        String txt = String.format("%s | %s", node, content);
        this.log(txt, level);
    }
}
