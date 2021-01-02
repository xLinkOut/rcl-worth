// @author Luca Cirillo (545480)

import java.io.IOException;
import java.net.ServerSocket;
import java.rmi.RemoteException;
import java.net.InetSocketAddress;
import java.nio.channels.Selector;
import java.rmi.registry.Registry;
import java.rmi.server.RemoteObject;
import java.nio.channels.SelectionKey;
import java.rmi.AlreadyBoundException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.nio.channels.ServerSocketChannel;

public class ServerMain extends RemoteObject implements Server, ServerRMI{
    // * TCP
    private static final int PORT_TCP = 6789;
    // * RMI
    private static final int PORT_RMI = 9876;
    private static final String NAME_RMI = "WORTH-SERVER";
    // * SERVER
    // TODO: boolean DEBUG

    public ServerMain(){
        // Persistenza
    }

    private void live(){
        // Variabili da utilizzare nel try/catch
        Selector selector = null;
        ServerSocket serverSocket;
        ServerSocketChannel serverSocketChannel;

        // * TCP Setup
        try{
            // Creo il ServerSocketChannel, ed implicitamente viene creato un ServerSocket
            serverSocketChannel = ServerSocketChannel.open();
            // Configuro il canale per funzionare in modalità non-blocking
            serverSocketChannel.configureBlocking(false);
            // Ottengo dal canale il ServerSocket creato implicitamente
            serverSocket = serverSocketChannel.socket();
            // Bindo il ServerSocket alla porta TCP specificata
            serverSocket.bind(new InetSocketAddress(PORT_TCP));
            // Creo il Selector chiamando la sua .open()
            selector = Selector.open();
            // e lo associo al canale, ponendolo in ascolto soltanto sulle "accept"
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
            System.out.println("[TCP] Listening on <"+PORT_TCP+">");
        }catch (IOException ioe){ ioe.printStackTrace(); } // TODO: Fail fast and exit?

        /* TODO: Controllo ridondante?
        // Controllo l'effettiva creazione del Selector
        if(selector == null){
            System.err.println("Qualcosa è andato storto durante la creazione del Selector!");
            System.exit(-1);
        }
        */

        // * RMI Setup
        try{
            // Esporto l'oggetto remoto <ServerRMI>
            ServerRMI stub = (ServerRMI) UnicastRemoteObject.exportObject(this, 0);
            // Creo il registro alla porta RMI specificata
            Registry registry = LocateRegistry.createRegistry(PORT_RMI);
            // Bindo lo stub al registry così da poter essere individuato
            // da altri hosts attraverso il nome <NAME_RMI>
            registry.bind(NAME_RMI, stub);
            System.out.println("[RMI] Bound on <"+NAME_RMI+">");
        } catch (RemoteException | AlreadyBoundException e) { e.printStackTrace(); }

    }

    public static void main(String[] args){
        ServerMain server = new ServerMain();
        server.live();
    }
}
