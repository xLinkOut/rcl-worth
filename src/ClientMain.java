// @author Luca Cirillo (545480)

import java.io.IOException;
import java.lang.reflect.Array;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.rmi.NotBoundException;
import java.rmi.registry.Registry;
import java.nio.channels.SocketChannel;
import java.rmi.registry.LocateRegistry;
import java.util.Scanner;

public class ClientMain {
    // * TCP
    private static final int PORT_TCP = 6789;
    private static final String IP_SERVER = "127.0.0.1";
    // * RMI
    private static final int PORT_RMI = 9876;
    private static final String NAME_RMI = "WORTH-SERVER";
    // * CLIENT
    private static boolean logged = false;
    private static String username = "Guest";

    // * MESSAGES

    public ClientMain(){ }

    private void run(){
        // Creo un SocketChannel per stabilire una connessione TCP
        SocketChannel socketChannel;
        // Creo uno stub per usare RMI
        ServerRMI server = null;

        try {
            // * TCP Setup
            try{
                // Apro un nuovo SocketChannel
                socketChannel = SocketChannel.open();
                // Connetto al server sul canale appena creato
                socketChannel.connect(new InetSocketAddress(IP_SERVER, PORT_TCP));
                System.out.println("[TCP] Connected to: " + socketChannel.getRemoteAddress());
            }catch (ConnectException ce){
                System.err.println("[TCP] connection refused, are you sure that server is up?");
                System.exit(-1);
            }

            // * RMI Setup
            try{
                // Creo un registry sulla porta RMI
                Registry registry = LocateRegistry.getRegistry(PORT_RMI);
                // Chiamo la lookup sullo stesso nome del server
                server = (ServerRMI) registry.lookup(NAME_RMI);
                System.out.println("[RMI] Connected to: " + server.toString());
            } catch (NotBoundException nbe) {
                System.err.println("[RMI] connection refused, are you sure that server is up?");
                System.exit(-1);
            }

            // TODO: Welcome message?

            // * CLI Setup
            Scanner scanner = new Scanner(System.in);
            String[] cmd;

            // * Registrazione e/o Login
            System.out.println("Login to use WORTH");
            while(!logged){
                System.out.print(username+"@WORTH > ");
                cmd = scanner.nextLine().split(" ");
                if(cmd.length > 0){
                    switch (cmd[0]){
                        case "register":
                            try{
                                if(server.register(cmd[1],cmd[2])){
                                    // Registrazione avvenuta con successo
                                    System.out.println("Registration was successful!\n"+
                                            "Wait, I try to automatically login to Worth...");
                                    // TODO: login()
                                }else{
                                    System.err.println("The username used is already in use! Try with "+username+"123 or X"+username+"X");
                                }
                            } catch (ArrayIndexOutOfBoundsException aioobe){
                                // Se almeno uno dei due parametri tra username e password
                                // non è presente, informo l'utente e stampo l'help del comando register
                                System.err.println("Oops! It looks like you haven't entered username or password!");
                                System.out.println("Usage: register username password");
                            }
                            break;
                        default:
                            System.err.println("Comando non riconosciuto o non disponibile da ospite, si prega di effettuare il login");
                    }
                }else{
                    System.err.println("Si prega di inserire un comando");
                }
            }

            // * Shell
        } catch (IOException e) { e.printStackTrace(); }
    }

    public static void main(String[] args){
        ClientMain client = new ClientMain();
        client.run();
    }
}
