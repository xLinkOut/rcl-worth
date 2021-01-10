// @author Luca Cirillo (545480)

import WorthExceptions.UsernameAlreadyTakenException;
import com.google.gson.Gson;

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
import java.util.List;
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
    private static List publicUsers;
    private static final Gson gson = new Gson();


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
        You need to login to WORTH in order to use it. Here some commands:
        \tregister username password | Create a new WORTH account;
        \tlogin    username password | Login to WORTH using your credentials;
        \thelp                       | Show this help;
        \tquit                       | Close WORTH.
        """;
    private static final String msgHelp =
        """
        \tcreateProject projectName    | Create a new project named <projectName>
        \taddMember
        \tshowMembers
        \taddCard
        \tlogout                     | Logout from your WORTH
        \thelp                       | Show this help;
        \tquit                       | Close WORTH.
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
                System.out.println("[TCP] connection refused, are you sure that server is up?");
                System.exit(-1);
            }

            // * RMI & CALLBACK Setup
            try{
                // Creo un registry sulla porta RMI
                Registry registry = LocateRegistry.getRegistry(PORT_RMI);
                // Chiamo la lookup sullo stesso nome del server
                server = (ServerRMI) registry.lookup(NAME_RMI);
                System.out.println("[RMI] Connected to: " + NAME_RMI);
                // Esporto l'oggetto Client per le callback
                notifyStub = (NotifyEventInterface)
                        UnicastRemoteObject.exportObject(this,0);
                //server.registerCallback(stub);
            } catch (NotBoundException nbe) {
                System.out.println("[RMI] connection refused, are you sure that server is up?");
                System.exit(-1);
            }

            // * CLI Setup
            Scanner scanner = new Scanner(System.in);
            String[] cmd;

            // * Shell
            System.out.println();
            System.out.println(msgHelpGuest);

            while(true){
                // * Registrazione e/o Login
                while(!logged){
                    System.out.print(username+"@WORTH > ");
                    cmd = scanner.nextLine().split(" ");
                    try {
                        if (cmd.length > 0) {
                            switch (cmd[0]) {
                                case "register":
                                    try {
                                        if(cmd[1].contains(":")) throw new IllegalArgumentException("Colon character (:) not allowed in username");
                                        if(cmd[2].contains(":")) throw new IllegalArgumentException("Colon character (:) not allowed in password");
                                        server.register(cmd[1], cmd[2]);
                                        // Registrazione avvenuta con successo
                                        System.out.println("Signup was successful!\nI try to automatically login to WORTH, wait..");
                                        // Provo ad effettuare il login automaticamente
                                        login(cmd[1], cmd[2]);

                                    } catch (IllegalArgumentException iae) {
                                        // Se almeno uno dei due parametri risulta invalido
                                        System.out.println(iae.getMessage());
                                        System.out.println("Usage: register username password");

                                    } catch (ArrayIndexOutOfBoundsException e) {
                                        // Se almeno uno dei due parametri tra username e password non è presente
                                        // oppure risulta vuoto, informo utente e stampo help del comando register
                                        System.out.println("Oops! It looks like you haven't entered username or password!\n" +
                                                "Usage: register username password");

                                    } catch (UsernameAlreadyTakenException uate) {
                                        // L'username utilizzato è già stato preso da un altro utente
                                        System.out.println("Username is already taken! Try with " + cmd[1] + "123 or X" + cmd[1] + "X");
                                    }
                                    break;

                                case "login":
                                    try {
                                        login(cmd[1], cmd[2]);
                                    } catch (IllegalArgumentException iae) {
                                        System.out.println("Insert a valid " + iae.getMessage() +
                                                "Usage: register username password");
                                    } catch (ArrayIndexOutOfBoundsException e) {
                                        // Se almeno uno dei due parametri tra username e password
                                        // non è presente o risulta vuoto, informo utente
                                        // e stampo help del comando register
                                        System.out.println("Oops! It looks like you haven't entered username or password!\n" +
                                                "Usage: login username password");
                                    }
                                    break;
                                case "logout":
                                    System.out.println("Mmh... what about some login instead?\n" +
                                            "Usage: login username password");
                                    break;
                                case "help":
                                    System.out.println(msgHelpGuest);
                                    break;
                                case "quit":
                                    System.out.println("Hope to see you soon," + username + "!");
                                    System.exit(0);
                                    break;
                                default:
                                    System.out.println("Command not recognized or not available as a guest, please login.");
                            }
                        } else { System.out.println(msgHelpGuest); }
                    }catch (IOException ioe){ioe.printStackTrace();}
                }

                // * Tutte le altre funzioni
                System.out.print(username+"@WORTH > ");
                cmd = scanner.nextLine().split(" ");
                try{
                    if(cmd.length > 0){
                        switch (cmd[0]){
                            case "register": // RMI
                                System.out.println("You are already logged in! Logout first, if you want to create another WORTH account");
                                break;
                            case "login": // TCP
                                System.out.println("You are already logged in! Logout first, if you want to login with another WORTH account");
                                break;
                            case "logout": // TCP
                                logout();
                                break;

                            case "createProject":
                                try{
                                    if(cmd[1].contains(":")) throw new IllegalArgumentException("Colon character (:) not allowed in project name");
                                    createProject(cmd[1]);
                                }catch(ArrayIndexOutOfBoundsException e){
                                    System.out.println("Every project need a name!\n" +
                                            "Usage: createProject projectName");
                                }catch (IllegalArgumentException iae){
                                    System.out.println(iae.getMessage());
                                }
                                break;

                            case "addMember":
                                try{
                                    addMember(cmd[1],cmd[2]);
                                }catch (ArrayIndexOutOfBoundsException e){
                                    System.out.println("Something is missing from your request...\n"+
                                            "Usage: addMember projectName memberUsername");
                                }
                                break;

                            case "showMembers":
                                showMembers(cmd[1]);
                                break;

                            case "addCard":
                                try {
                                    if (cmd[1].contains(":")) throw new IllegalArgumentException("Colon character (:) not allowed in username\n");
                                    // TODO: Unire la caption su cmd[3]
                                    addCard(cmd[1], cmd[2], cmd[3]);
                                } catch (IllegalArgumentException iae) {
                                    System.out.println(iae.getMessage());
                                } catch (ArrayIndexOutOfBoundsException e){
                                    System.out.println("Something is missing from your request...\n"+
                                            "Usage: addCard projectName cardName cardCaption");
                                }
                                break;


                            case "help":
                                System.out.println(msgHelp);
                                break;
                            case "quit":
                                if(logged) logout();
                                System.out.println("Hope to see you soon,"+username+"!");
                                System.exit(0);
                                break;
                            default:
                                System.out.println("Command not recognized. Type help if you're stuck!");
                        }
                    }else{ System.out.println(msgHelpGuest); }
                } catch (IOException ioe){ioe.printStackTrace();}
            }

            // unregisterCallback
            // socketChannel close
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void login(String username, String password)
            throws IllegalArgumentException, IOException {
        // Controllo validità dei parametri
        if(username.isEmpty()) throw new IllegalArgumentException("username");
        if(password.isEmpty()) throw new IllegalArgumentException("password");

        // Invio al server il comando di login con le credenziali
        sendRequest("login "+username+" "+password);
        // Se tutto ok, aggiorno i parametri, registro il client
        // per le callbacks e ritorno true
        String[] response = readResponse().split(":");
        if(response[0].equals("ok")){
            // Registro l'esito positivo del login
            logged = true;
            ClientMain.username = username;
            // Registro il client per la ricezione delle callback
            server.registerCallback(notifyStub);
            System.out.println(response[1]);
        }else {
            // Stampo il codice dell'operazione soltanto se sono in modalità debug
            //if(DEBUG) System.out.print("["+response[1]+"] ");
            System.out.println(response[2]);
        }
    }

    private void logout() throws IOException {
        // Invio al server il comando di logout
        sendRequest("logout "+username);
        // Se tutto ok
        String[] response = readResponse().split(":");
        if(response[0].equals("ok")){
            // Registro l'esito positivo del logout
            logged = false;
            username = "Guest";
            // Disiscrivo il client dalla ricezione delle callbacks
            server.unregisterCallback(notifyStub);
            System.out.println(response[1]);
        }else {
            //if(DEBUG) System.out.print("["+response[1]+"] ");
            System.out.println(response[2]);
        }
    }

    private void createProject(String projectName)
            throws IllegalArgumentException, IOException {
        // Controllo validità dei parametri
        if(projectName.isEmpty()) throw new IllegalArgumentException("projectName");

        // Invio al server il comando per creare un nuovo progetto
        sendRequest("createProject "+username+" "+projectName);
        // Se tutto ok
        String[] response = readResponse().split(":");
        if(response[0].equals("ok")){
            System.out.println(response[1]);
        }else {
            //if(DEBUG) System.out.print("["+response[1]+"] ");
            System.out.println(response[2]);
        }
    }

    private void addMember(String projectName, String memberUsername)
            throws IllegalArgumentException, IOException {
        // Controllo validità dei parametri
        if(projectName.isEmpty()) throw new IllegalArgumentException("projectName");
        if(memberUsername.isEmpty()) throw new IllegalArgumentException("memberUsername");

        // Invio al server il comando per aggiungere un nuovo utente al progetto
        sendRequest("addMember "+username+" "+projectName+" "+memberUsername);
        String[] response = readResponse().split(":");
        if(response[0].equals("ok")){
            System.out.println(response[1]);
        }else {
            //if(DEBUG) System.out.print("["+response[1]+"] ");
            System.out.println(response[2]);
        }
    }

    private void showMembers(String projectName)
            throws IllegalArgumentException, IOException {
        // Controllo validità dei parametri
        if(projectName.isEmpty()) throw new IllegalArgumentException("projectName");

        sendRequest("showMembers "+username+" "+projectName);
        String[] response = readResponse().split(":");
        if(response[0].equals("ok")){
            System.out.println("These are the members of the project "+projectName+"\n");
            // [User@48140564, User@58ceff1]
        }else {
            //if(DEBUG) System.out.print("["+response[1]+"] ");
            System.out.println(response[2]);
        }
    }

    private void addCard(String projectName, String cardName, String cardCaption)
            throws IllegalArgumentException, IOException {
        // Controllo validità dei parametri
        if(projectName.isEmpty()) throw new IllegalArgumentException("projectName");
        if(cardName.isEmpty()) throw new IllegalArgumentException("cardName");
        if(cardCaption.isEmpty()) throw new IllegalArgumentException("cardCaption");

        sendRequest("addCard "+username+" "+projectName+" "+cardName+" "+cardCaption);
        String[] response = readResponse().split(":");
        if(response[0].equals("ok")){
            System.out.println(response[1]);
        }else {
            //if(DEBUG) System.out.print("["+response[1]+"] ");
            System.out.println(response[2]);
        }

    }
    // * UTILS
    private void sendRequest(String request) throws IOException {
        socketChannel.write(ByteBuffer.wrap((request).getBytes(StandardCharsets.UTF_8)));
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

        //if(DEBUG) System.out.println("Server@WORTH < "+serverResponse.toString());
        return serverResponse.toString();
    }

    @Override
    public void notifyEvent(List publicUsers) throws RemoteException {
        ClientMain.publicUsers = publicUsers;
        //System.out.println(ClientMain.publicUsers);
    }

    public static void main(String[] args){
        ClientMain client = new ClientMain();
        client.run();
    }

}
