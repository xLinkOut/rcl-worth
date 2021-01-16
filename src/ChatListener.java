// @author Luca Cirillo (545480)

import java.io.IOException;
import java.net.InetAddress;
import java.net.DatagramPacket;
import java.net.MulticastSocket;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ChatListener implements Runnable{

    // Indirizzo IP multicast del progetto
    private final String multicastIP;
    // Porta dedicata al progetto
    private final int multicastPort;
    // Coda in cui inserire i messaggi in ingresso
    /* La ConcurrentLinkedQueue è unbounded, garantendo spazio a sufficienza per
       immagazzinare messaggi in arrivo dagli altri utenti del progetto,
       è thread-safe, permettendo la lettura da parte del thread Main dei messaggi
       senza interferire con la scrittura di nuovi messaggi, ed infine
       è una coda (FIFO), e permette di leggere in ordine i messaggi,
       dal più vecchio al più recente. I nuovi messaggi vengono infatti inseriti
       in coda, mentre la lettura inizia proprio dalla testa.
    */
    private final ConcurrentLinkedQueue<String> messagesQueue;

    public ChatListener(String multicastIP, int multicastPort, ConcurrentLinkedQueue<String> messagesQueue) {
        this.multicastIP = multicastIP;
        this.multicastPort = multicastPort;
        this.messagesQueue = messagesQueue;
    }

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
            // Unisco al gruppo
            multicastSocket.joinGroup(inetAddress);
            packet = new DatagramPacket(new byte[8192],8192);
        } catch (IOException e) {
            e.printStackTrace();
           System.out.println("Qualcosa è andato storto mentre provavo a raggiungere la chat di progetto...");
        }

        // Controllo "ridondante" per evitare warning
        if(multicastSocket == null || packet == null){
            System.out.println("Qualcosa è andato storto mentre provavo a raggiungere la chat di progetto...");
            return;
        }

        try{
            while(true) {
                // Ricevo nuovi messaggi da altri utenti
                multicastSocket.receive(packet);
                // Li aggiungo alla coda
                messagesQueue.add(new String(packet.getData(),0,packet.getLength()));
            }
        } catch (IOException e) { System.out.println("Qualcosa è andato storto mentre aspettavo messaggi sulla chat..."); }


        // Alla fine, abbandono il gruppo
        try { multicastSocket.leaveGroup(inetAddress);
        } catch (IOException ignored) {}

        // e chiudo il socket
        multicastSocket.close();
    }
}
