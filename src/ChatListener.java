import java.util.concurrent.ConcurrentLinkedQueue;

public class ChatListener implements Runnable{

    private final String multicastIP;
    private final int multicastPort;
    private final ConcurrentLinkedQueue<String> messageBuffer;

    public ChatListener(String multicastIP, int multicastPort, ConcurrentLinkedQueue<String> messageBuffer) {
        this.multicastIP = multicastIP;
        this.multicastPort = multicastPort;
        this.messageBuffer = messageBuffer;
    }

    @Override
    public void run() {
        for(int i=0;i<10;i++){
            messageBuffer.add(String.valueOf(System.currentTimeMillis()));
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
