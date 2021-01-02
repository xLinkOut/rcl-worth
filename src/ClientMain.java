// @author Luca Cirillo (545480)

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.rmi.NotBoundException;
import java.rmi.registry.Registry;
import java.nio.channels.SocketChannel;
import java.rmi.registry.LocateRegistry;

public class ClientMain {
    // * TCP
    private static final int PORT_TCP = 6789;
    private static final String IP_SERVER = "127.0.0.1";
    // * RMI
    private static final int PORT_RMI = 9876;
    private static final String NAME_RMI = "WORTH-SERVER";

    public ClientMain(){ }

    private void run(){
        // Creo un SocketChannel per stabilire una connessione TCP
        SocketChannel socketChannel;

        try {
            // * TCP Setup
            try{
                // Apro un nuovo SocketChannel
                socketChannel = SocketChannel.open();
                // Connetto al server sul canale appena creato
                socketChannel.connect(new InetSocketAddress(IP_SERVER, PORT_TCP));
                System.out.println("[TCP] Connected to: " + socketChannel.getRemoteAddress());
            }catch (ConnectException ce){
                System.err.println("TCP connection refused, are you sure that server is up?");
                System.exit(-1);
            }

            // * RMI Setup
            try{
                // Creo un registry sulla porta RMI
                Registry registry = LocateRegistry.getRegistry(PORT_RMI);
                // Chiamo la lookup sullo stesso nome del server
                ServerRMI server = (ServerRMI) registry.lookup(NAME_RMI);
                System.out.println("[RMI] Connected to: " + server.toString());
            } catch (NotBoundException nbe) {
                System.err.println("RMI connection refused, are you sure that server is up?");
                System.exit(-1);
            }

        } catch (IOException e) { e.printStackTrace(); }
    }

    public static void main(String[] args){
        ClientMain client = new ClientMain();
        client.run();
    }
}
