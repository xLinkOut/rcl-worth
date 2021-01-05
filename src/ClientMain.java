// @author Luca Cirillo (545480)

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.Registry;
import java.nio.channels.SocketChannel;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.RemoteObject;
import java.rmi.server.UnicastRemoteObject;
import java.util.Scanner;

public class ClientMain extends RemoteObject implements NotifyEventInterface {
    // * TCP
    private static final int PORT_TCP = 6789;
    private static final String IP_SERVER = "127.0.0.1";
    private static SocketChannel socketChannel; // TCP
    // * RMI
    private static final int PORT_RMI = 9876;
    private static final String NAME_RMI = "WORTH-SERVER";
    private static ServerRMI server = null;
    private static NotifyEventInterface notifyStub;
    // * CLIENT
    private static boolean logged = false;
    private static String username = "Guest";

    // * MESSAGES
    private static final String msgStartup =
        """
        
        ██╗    ██╗ ██████╗ ██████╗ ████████╗██╗  ██╗
        ██║    ██║██╔═══██╗██╔══██╗╚══██╔══╝██║  ██║
        ██║ █╗ ██║██║   ██║██████╔╝   ██║   ███████║
        ██║███╗██║██║   ██║██╔══██╗   ██║   ██╔══██║
        ╚███╔███╔╝╚██████╔╝██║  ██║   ██║   ██║  ██║
         ╚══╝╚══╝  ╚═════╝ ╚═╝  ╚═╝   ╚═╝   ╚═╝  ╚═╝
                                                   \s""";
    private static final String msgHelpGuest =
        """
        You need to login to WORTH in order to use it.
        \tregister username password |\tCreate a new WORTH account;
        \tlogin username password |\tLogin to WORTH using your credentials;
        \thelp |\tShow this help;
        \tquit |\tClose WORTH.""";
    private static final String msgHelp =
        """
        Bro, you stuck?            
        """;

    public ClientMain(){
        super(); // Callback
    }

    private void run(){
        // Messaggio di avvio
        System.out.println(msgStartup);

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

            // * RMI & CALLBACK Setup
            try{
                // Creo un registry sulla porta RMI
                Registry registry = LocateRegistry.getRegistry(PORT_RMI);
                // Chiamo la lookup sullo stesso nome del server
                server = (ServerRMI) registry.lookup(NAME_RMI);
                System.out.println("[RMI] Connected to: " + server.toString());
                // Esporto l'oggetto Client per le callback
                notifyStub = (NotifyEventInterface)
                        UnicastRemoteObject.exportObject(this,0);
                //server.registerCallback(stub);
            } catch (NotBoundException nbe) {
                System.err.println("[RMI] connection refused, are you sure that server is up?");
                System.exit(-1);
            }


            // * CLI Setup
            Scanner scanner = new Scanner(System.in);
            String[] cmd;

            // * Registrazione e/o Login
            System.out.println(msgHelpGuest);
            while(!logged){
                System.out.print(username+"@WORTH > ");
                cmd = scanner.nextLine().split(" ");
                if(cmd.length > 0){
                    switch (cmd[0]){
                        case "register":
                            try {
                                if (server.register(cmd[1], cmd[2])) {
                                    // Registrazione avvenuta con successo
                                    System.out.println("Signup was successful!\n" +
                                        "I try to automatically login to WORTH, wait..");
                                    if(login(cmd[1], cmd[2])){
                                        logged = true;
                                        username = cmd[1];
                                        System.out.println("Great! Now your are logged as "+cmd[1]+"!");
                                    }else{
                                        System.err.println("Something went wrong during automatically login, try to manually login");
                                    }
                                } else {
                                    System.err.println("Username is already taken! Try with " + cmd[1] + "123 or X" + cmd[1] + "X");
                                }
                            } catch (IllegalArgumentException iae){
                                System.err.println("Insert a valid " + iae.getMessage() +
                                    "Usage: register username password");
                            } catch (ArrayIndexOutOfBoundsException e){
                                // Se almeno uno dei due parametri tra username e password
                                // non è presente o risulta vuoto, informo utente
                                // e stampo help del comando register
                                System.err.println("Oops! It looks like you haven't entered username or password!\n" +
                                    "Usage: register username password");
                            }
                            break;
                        case "login":
                            try {
                                if (login(cmd[1], cmd[2])) {
                                    logged = true;
                                    username = cmd[1];
                                    System.out.println("Great! Now your are logged as " + cmd[1] + "!");
                                } else {
                                    System.err.println("Are you sure that an account with this name exists?\n" +
                                            "If you need one, use register command");
                                }
                            } catch (IllegalArgumentException iae){
                                    System.err.println("Insert a valid " + iae.getMessage() +
                                            "Usage: register username password");
                                } catch (ArrayIndexOutOfBoundsException e){
                                    // Se almeno uno dei due parametri tra username e password
                                    // non è presente o risulta vuoto, informo utente
                                    // e stampo help del comando register
                                    System.err.println("Oops! It looks like you haven't entered username or password!\n" +
                                            "Usage: login username password");
                                }
                            break;
                        case "help":
                            System.out.println(msgHelpGuest);
                            break;
                        case "quit":
                            System.out.println("Hope to see you soon,"+username+"!");
                            System.exit(0);
                            break;
                        default:
                            System.err.println("Command not recognized or not available as a guest, please login.");
                    }
                }else{
                    System.out.println(msgHelpGuest);
                }
            }

            // * Shell
            while(true){
                System.out.print(username+"@WORTH > ");
                cmd = scanner.nextLine().split(" ");
                if(cmd.length > 0){
                    switch (cmd[0]){
                        case "help":
                            System.out.println(msgHelp);
                            break;
                        case "quit":
                            System.out.println("Hope to see you soon,"+username+"!");
                            // Fare logout?
                            System.exit(0);
                            break;
                        default:
                            System.err.println("Command not recognized or not available as a guest, please login.");
                    }
                }else{
                    System.out.println(msgHelpGuest);
                }
            }

            // unregisterCallback
            // socketChannel close
        } catch (IOException e) { e.printStackTrace(); }
    }

    private boolean login(String username, String password){
        try {
            socketChannel.write(ByteBuffer.wrap(("login "+username+" "+password).getBytes(StandardCharsets.UTF_8)));
            server.registerCallback(notifyStub);
            return readResponse().equals("ok");
        } catch (IOException e) {e.printStackTrace(); return false;}
    }

    private String readResponse() throws IOException {
        // Alloco un buffer di <DIM_BUFFER>
        ByteBuffer msgBuffer = ByteBuffer.allocate(1024);
        // Stringa corrispondente alla risposta del server
        StringBuilder serverResponse = new StringBuilder();
        // Quantità di bytes letti ad ogni chiamata della .read()
        int bytesRead;

        do {
            // Svuoto il buffer
            msgBuffer.clear();
            // Leggo dal canale direttamente nel buffer
            // e mi salvo la quantità di bytes letti
            bytesRead = socketChannel.read(msgBuffer);
            // Passo il buffer dalla modalità lettura a scrittura
            msgBuffer.flip();
            // Costruisco la risposta del server appendendoci
            // la stringa letta (finora) dal canale
            serverResponse.append(StandardCharsets.UTF_8.decode(msgBuffer).toString());
            // Riporto il buffer in modalità lettura
            msgBuffer.flip();

            // Finché ci sono bytes da leggere, continuo
        } while (bytesRead >= 1024);

        // Alla fine, riporto il buffer in modalità scrittura
        msgBuffer.flip();
        System.out.println(serverResponse);
        return serverResponse.toString();
    }

    public static void main(String[] args){
        ClientMain client = new ClientMain();
        client.run();
    }

    @Override
    public void notifyEvent(int value) throws RemoteException {
        System.out.println("Update event received: "+value);
    }
}
