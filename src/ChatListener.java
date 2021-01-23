// @author Luca Cirillo (545480)

import java.util.Queue;
import java.io.IOException;
import java.net.InetAddress;
import java.net.DatagramPacket;
import java.net.MulticastSocket;
import java.util.concurrent.ConcurrentLinkedQueue;

// TODO: riunire tutto sotto lo stesso try/catch

// Thread in ascolto sulla chat di progetto per ricevere i messaggi
public class ChatListener implements Runnable{

    private final String multicastIP; // Indirizzo IP multicast del progetto
    private final int multicastPort;  // Porta dedicata al progetto
    private final Queue<String> messagesQueue; // Coda dei messaggi in ingresso
    /* La ConcurrentLinkedQueue è la struttura dati più adatta per mantenere
       i messaggi in ingresso e permettere all'utente di leggerli in qualsiasi momento
        - Unbounded: spazio a sufficienza per salvare i messaggi in arrivo;
        - Thread-safe: la lettura da parte del thread Main non interferisce
            con la scrittura di nuovi messaggi;
        - Coda: messaggi salvati in ordine (FIFO).
    */

    public ChatListener(String multicastIP, int multicastPort,
                        ConcurrentLinkedQueue<String> messagesQueue) {
        this.multicastIP = multicastIP;
        this.multicastPort = multicastPort;
        this.messagesQueue = messagesQueue;
    }

    // Getters
    public String getMulticastIP(){ return this.multicastIP; }
    public int getMulticastPort(){ return this.multicastPort; }

    @Override
    public void run() {
        // Dichiaro fuori dal try/catch tutto il necessario
        InetAddress inetAddress = null;
        MulticastSocket multicastSocket = null;
        DatagramPacket packet = null;
        try {
            inetAddress = InetAddress.getByName(multicastIP);
            multicastSocket = new MulticastSocket(multicastPort);
            // Mi unisco al gruppo
            multicastSocket.joinGroup(inetAddress);
            packet = new DatagramPacket(new byte[8192],8192);
        } catch (IOException ioe) {
            ioe.printStackTrace();
            System.out.println("Something went wrong while trying to reach the project chat ...");
        }

        // Controllo "ridondante" per evitare warning
        if(multicastSocket == null || packet == null){
            System.out.println("Something went wrong while trying to reach the project chat ...");
            return;
        }

        try{
            while(!Thread.currentThread().isInterrupted()) {
                // Ricevo nuovi messaggi da altri utenti
                multicastSocket.receive(packet);
                // Li aggiungo alla coda
                messagesQueue.add(new String(packet.getData(),0,packet.getLength()));
            }
        } catch (IOException ioe) {
            System.out.println("Something went wrong while I was waiting for messages on the chat ...");
        }


        try {
            // Alla fine, abbandono il gruppo
            multicastSocket.leaveGroup(inetAddress);
            // e chiudo il socket
            multicastSocket.close();
        } catch (IOException ignored) {}

    }
}
