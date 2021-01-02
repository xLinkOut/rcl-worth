// @author Luca Cirillo (545480)

import java.io.IOException;
import java.net.ServerSocket;
import java.net.InetSocketAddress;
import java.nio.channels.Selector;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;

public class Server {

    private static final int PORT_TCP = 6789;

    public static void main(String[] args){

        // Creo le variabili da utilizzare nel try/catch
        Selector selector = null;
        ServerSocket serverSocket;
        ServerSocketChannel serverSocketChannel;

        try{
            // Creo il ServerSocketChannel, ed implicitamente viene creato un ServerSocket
            serverSocketChannel = ServerSocketChannel.open();
            // Configuro il canale per funzionare in modalità non-blocking
            serverSocketChannel.configureBlocking(false);
            // Ottengo dal canale il ServerSocket creato implicitamente
            serverSocket = serverSocketChannel.socket();
            // Bindo il ServerSocket alla porta TCP sopra specificata
            serverSocket.bind(new InetSocketAddress(PORT_TCP));
            // Creo il Selector chiamando la sua .open()
            selector = Selector.open();
            // e lo associo al canale, ponendolo in ascolto soltanto sulle "accept"
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        }catch (IOException ioe){ ioe.printStackTrace(); }

        // Controllo l'effettiva creazione del Selector
        if(selector == null){
            System.out.println("Qualcosa è andato storto durante la creazione del Selector!");
            System.exit(-1);
        }

        System.out.println("Server in attesa di connessioni sulla porta <"+PORT_TCP+">\n");

        while(true){
            try {
                //System.out.println("Aspetto...");
                // Seleziona un insieme di keys che corrispondono a canali pronti ad eseguire operazioni
                selector.select();
            } catch (IOException ioe){ ioe.printStackTrace(); break; }
            System.out.println(selector.selectedKeys());
        }
    }
}
