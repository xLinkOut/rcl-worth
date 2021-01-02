// @author Luca Cirillo (545480)

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.channels.*;
import java.rmi.RemoteException;
import java.net.InetSocketAddress;
import java.rmi.registry.Registry;
import java.rmi.server.RemoteObject;
import java.rmi.AlreadyBoundException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Iterator;

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

    private void live() {
        // Variabili da utilizzare nel try/catch
        Selector selector = null;
        ServerSocket serverSocket;
        ServerSocketChannel serverSocketChannel;

        // * TCP Setup
        try {
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
            System.out.println("[TCP] Listening on <" + PORT_TCP + ">");
        } catch (IOException ioe) {
            ioe.printStackTrace();
            System.exit(-1);
        }

        // Controllo l'effettiva creazione del Selector
        if (selector == null) {
            System.err.println("Something went wrong while creating the Selector!");
            System.exit(-1);
        }

        // * RMI Setup
        try {
            // Esporto l'oggetto remoto <ServerRMI>
            ServerRMI stub = (ServerRMI) UnicastRemoteObject.exportObject(this, 0);
            // Creo il registro alla porta RMI specificata
            Registry registry = LocateRegistry.createRegistry(PORT_RMI);
            // Bindo lo stub al registry così da poter essere individuato
            // da altri hosts attraverso il nome <NAME_RMI>
            registry.bind(NAME_RMI, stub);
            System.out.println("[RMI] Bound on <" + NAME_RMI + ">");
        } catch (RemoteException | AlreadyBoundException e) {
            e.printStackTrace();
            System.exit(-2);
        }

        // Serve forever
        while (true) {
            try {
                // Seleziona un insieme di keys che corrispondono a canali pronti ad eseguire operazioni
                selector.select();
            } catch (IOException ioe) {
                ioe.printStackTrace();
                break;
            }

            // Iteratore sui canali che risultano pronti
            Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();

            while (iterator.hasNext()) {
                // Prendo dall'iteratore la prossima key
                SelectionKey key = iterator.next();
                // La rimuovo esplicitamente dall'iteratore
                iterator.remove();

                // Se la key considerata identifica:
                try {

                    // Un canale pronto ad accettare una nuova connessione
                    if (key.isAcceptable()) {
                        ServerSocketChannel socket = (ServerSocketChannel) key.channel();
                        // Accetto la connessione
                        SocketChannel client = socket.accept();
                        System.out.println("<" + client.getRemoteAddress() + ">: Connessione accettata");
                        client.configureBlocking(false);
                        // Registro il nuovo client sul Selector
                        client.register(selector, SelectionKey.OP_READ, null);
                    }

                } catch (ClosedChannelException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void main(String[] args){
        ServerMain server = new ServerMain();
        server.live();
    }
}
