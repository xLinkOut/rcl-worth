import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.UnknownHostException;
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
        // Dichiaro fuori dal try/catch tutto il necessario
        InetAddress inetAddress;
        MulticastSocket multicastSocket = null;
        DatagramPacket packet = null;
        try {
            inetAddress = InetAddress.getByName(multicastIP);
            multicastSocket = new MulticastSocket(multicastPort);
            multicastSocket.joinGroup(inetAddress);
            packet = new DatagramPacket(new byte[8192],8192);
        } catch (UnknownHostException e) {
            // Controllo errori
            e.printStackTrace();
        } catch (IOException e) {
            // Controllo errori
            e.printStackTrace();
        }

        while(true) { // while not interrupted
            // Facciamo l'IDE contento
            assert multicastSocket != null;
            assert packet != null;
            try{
                multicastSocket.receive(packet);
                messageBuffer.add(new String(packet.getData(),0,packet.getLength()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        /*
        // Rimuovo il multicastSocket dal gruppo
        try {
            multicastSocket.leaveGroup(inetAddress);
        } catch (IOException e) { e.printStackTrace(); }

        // Chiudo il socket
        multicastSocket.close();
         */
    }
}












