// @author Luca Cirillo (545480)

import java.io.IOException;
import java.net.InetAddress;
import java.net.DatagramPacket;
import java.net.MulticastSocket;
import java.util.concurrent.ConcurrentLinkedQueue;

// TODO: dimensione del pacchetto?
// TODO: Cambiare il testo dei messaggi su console
// TODO: cambiare l'interfaccia con Queue
// TODO: riunire tutto sotto lo stesso try/catch

// Thread in ascolto sulla chat di progetto per ricevere i messaggi
public class ChatListener implements Runnable{

    private final String multicastIP; // Indirizzo IP multicast del progetto
    private final int multicastPort;  // Porta dedicata al progetto
    /* La ConcurrentLinkedQueue è la struttura dati più adatta per mantenere
       i messaggi in ingresso e permettere all'utente di leggerli in qualsiasi momento
        - Unbounded: spazio a sufficienza per salvare i messaggi in arrivo;
        - Thread-safe: la lettura da parte del thread Main non interferisce
            con la scrittura di nuovi messaggi;
        - Coda: messaggi salvati in ordine (FIFO).
    */
    private final ConcurrentLinkedQueue<String> messagesQueue; // Coda dei messaggi in ingresso

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
            System.out.println("Qualcosa è andato storto mentre provavo a raggiungere la chat di progetto...");
        }

        // Controllo "ridondante" per evitare warning
        if(multicastSocket == null || packet == null){
            System.out.println("Qualcosa è andato storto mentre provavo a raggiungere la chat di progetto...");
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
            System.out.println("Qualcosa è andato storto mentre aspettavo messaggi sulla chat...");
        }


        try {
            // Alla fine, abbandono il gruppo
            multicastSocket.leaveGroup(inetAddress);
            // e chiudo il socket
            multicastSocket.close();
        } catch (IOException ignored) {}

    }
}
