// @author Luca Cirillo (545480)

import WorthExceptions.ProjectNotFoundException;
import WorthExceptions.UsernameAlreadyTakenException;
import com.google.gson.Gson;
import jdk.jfr.MetadataDefinition;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.Registry;
import java.nio.channels.SocketChannel;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.RemoteObject;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

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
    private static Map<String, ConcurrentLinkedQueue<String>> chats;
    private static Map<String, ChatListener> projectMulticastIP;
    private static List<Thread> chatListeners;
    private static DatagramSocket multicastSocket;

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
        \tcreateProject <projectName>                 | Create a new project
        \taddMember <projectName> <memberUsername>    | Add a new member in a project
        \taddCard <projectName> <cardName> <cardDesc> | Create and assign a new card to a project
        \tshowMembers <projectName>                   | Shows the current members of a project
        \tshowCard <projectName> <cardName>           | Shows information about a card assigned to a project
        \tshowCards <projectName>                     | Shows all cards assigned to a project 
        \tmoveCard ---
        \tgetCardHistory ---
        \tcancelProject ---
        \tlistUsers ---
        \tlistOnlineUsers ---
        \treadChat ---
        \tsendChatMsg ---
        \tlogout                                       | Logout from your WORTH account
        \thelp                                         | Show this help;
        \tquit                                         | Close WORTH.
        """;

    public ClientMain(){
        super(); // Callback

        // Chats
        this.chats = new HashMap<>();
        this.chatListeners = new LinkedList<>();
        this.projectMulticastIP = new LinkedHashMap<>();
        try {
            this.multicastSocket = new DatagramSocket();
        } catch (SocketException e) {
            e.printStackTrace();
            System.exit(-1);
        }
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
                                        // cmd[1] = username, nome utente dell'account che si vuole creare
                                        // cmd[2] = password, password di accesso scelta per l'account
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
                                        // cmd[1] = username, nome utente del proprio account
                                        // cmd[2] = password, password del proprio account
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
                                    socketChannel.close();
                                    System.out.println("Hope to see you soon, " + username + "!");
                                    System.exit(0);
                                    break;
                                default:
                                    System.out.println("Command not recognized or not available as a guest, please login.");
                            }
                        } else { System.out.println(msgHelpGuest); }
                    }catch (IOException ioe){
                        System.out.println("WORTH seems to be unreachable... try again in a few moments, apologize for the inconvenience.");
                        System.exit(-1);
                    }
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
                                    // cmd[1] = projectName, nome del progetto
                                    if(cmd[1].contains(":"))
                                        throw new IllegalArgumentException("Colon character (:) not allowed in project name");
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
                                    // cmd[1] = projectName, nome del progetto
                                    // cmd[2] = memberUsername, username dell'utente da inserire
                                    addMember(cmd[1],cmd[2]);
                                }catch (ArrayIndexOutOfBoundsException e){
                                    System.out.println("Something is missing from your request...\n"+
                                            "Usage: addMember projectName memberUsername");
                                }catch (IllegalArgumentException iae){
                                    System.out.println(iae.getMessage());
                                }
                                break;

                            case "showMembers":
                                try{
                                    // cmd[1] = projectName, nome del progetto
                                    showMembers(cmd[1]);
                                }catch (ArrayIndexOutOfBoundsException e) {
                                    System.out.println("Something is missing from your request...\n" +
                                            "Usage: showMembers projectName");
                                }catch (IllegalArgumentException iae) {
                                    System.out.println(iae.getMessage());
                                }
                                break;

                            case "addCard":
                                try {
                                    // cmd[1] = projectName, nome del progetto
                                    // cmd[2] = cardName, nome della card
                                    // cmd[3] = cardDescription, descrizione testuale della card
                                    if (cmd[1].contains(":")) throw new IllegalArgumentException("Colon character (:) not allowed in username\n");
                                    // TODO: Unire la caption su cmd[3]
                                    addCard(cmd[1], cmd[2], cmd[3]);
                                }catch (ArrayIndexOutOfBoundsException e){
                                    System.out.println("Something is missing from your request...\n"+
                                            "Usage: addCard projectName cardName cardCaption");
                                }catch (IllegalArgumentException iae) {
                                    System.out.println(iae.getMessage());
                                }
                                break;

                            case "showCard":
                                try{
                                    // cmd[1] = projectName, nome del progetto
                                    // cmd[2] = cardName, nome della card
                                    showCard(cmd[1],cmd[2]);
                                } catch (ArrayIndexOutOfBoundsException e) {
                                    System.out.println("Something is missing from your request...\n"+
                                            "Usage: showCard projectName cardName");
                                } catch (IllegalArgumentException iae) {
                                    System.out.println(iae.getMessage());
                                }
                                break;

                            case "listProjects":
                                listProjects();
                                break;

                            case "showCards":
                                try{
                                    // cmd[1] = projectName, nome del progetto
                                    // table  = true if the user wants a table version of the cards
                                    showCards(cmd[1], cmd.length == 3);
                                } catch (ArrayIndexOutOfBoundsException e) {
                                    System.out.println("Something is missing from your request...\n"+
                                            "Usage: showCards projectName");
                                } catch (IllegalArgumentException iae) {
                                    System.out.println(iae.getMessage());
                                }
                                break;

                            case "moveCard":
                                try{
                                    // cmd[1] = projectName, nome del progetto
                                    // cmd[2] = cardName, nome della card
                                    // cmd[3] = from, lista dove si trova attualmente la card
                                    // cmd[4] = to, lista in cui si desidera spostare la card
                                    moveCard(cmd[1],cmd[2],cmd[3],cmd[4]);
                                } catch (ArrayIndexOutOfBoundsException e) {
                                    System.out.println("Something is missing from your request...\n"+
                                            "Usage: moveCard projectName cardName from to");
                                } catch (IllegalArgumentException iae) {
                                    System.out.println(iae.getMessage());
                                }
                                break;

                            case "getCardHistory":
                                try{
                                    // cmd[1] = projectName, nome del progetto
                                    // cmd[2] = cardName, nome della card
                                    getCardHistory(cmd[1],cmd[2]);
                                } catch (ArrayIndexOutOfBoundsException e) {
                                    System.out.println("Something is missing from your request...\n" +
                                            "Usage: getCardHistory projectName cardName");
                                } catch(IllegalArgumentException iae){
                                    System.out.println(iae.getMessage());
                                }
                                break;

                            case "cancelProject":
                                try{
                                    // cmd[1] = projectName, nome del progetto
                                    cancelProject(cmd[1]);
                                } catch (ArrayIndexOutOfBoundsException e) {
                                    System.out.println("Something is missing from your request...\n" +
                                            "Usage: cancelProject projectName");
                                } catch(IllegalArgumentException iae){
                                    System.out.println(iae.getMessage());
                                }
                                break;

                            case "readChat":
                                try{
                                    // cmd[1] = projectName, nome del progetto
                                    readChat(cmd[1]);
                                } catch (ProjectNotFoundException e) {
                                    System.out.println("Can't found "+cmd[1]+", are you sure that exists? Try createProject to create a project");
                                } catch (ArrayIndexOutOfBoundsException e) {
                                    System.out.println("Something is missing from your request...\n" +
                                            "Usage: readChat projectName");
                                } catch(IllegalArgumentException iae){
                                    System.out.println(iae.getMessage());
                                }
                                break;

                            case "sendChatMsg":
                                try{
                                    // cmd[1] = projectName, nome del progetto
                                    // cmd[2] ... cmd[n] = testo del messaggio da inviare
                                    // (viene "ricostruito" in una stringa essendo cmd un array)
                                    sendChatMsg(cmd[1],String.join(" ",Arrays.copyOfRange(cmd,2,cmd.length)));
                                } catch (ProjectNotFoundException e) {
                                    System.out.println("Can't found "+cmd[1]+", are you sure that exists? Try createProject to create a project");
                                } catch (ArrayIndexOutOfBoundsException e) {
                                    System.out.println("Something is missing from your request...\n" +
                                            "Usage: sendChatMsg projectName message");
                                } catch(IllegalArgumentException iae){
                                    System.out.println(iae.getMessage());
                                }
                                break;


                            case "help":
                                System.out.println(msgHelp);
                                break;
                            case "quit":
                                if(logged){
                                    server.unregisterCallback(username,notifyStub);
                                    logout();
                                }
                                socketChannel.close();
                                System.out.println("Hope to see you soon,"+username+"!");
                                System.exit(0);
                                break;
                            default:
                                System.out.println("Command not recognized. Type help if you're stuck!");
                        }
                    }else{ System.out.println(msgHelpGuest); }
                } catch (IOException ioe){ioe.printStackTrace();}
            }

        } catch (IOException e) {
            System.out.println("WORTH seems to be unreachable... try again in a few moments, apologize for the inconvenience.");
            System.exit(-1);
        }
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
        String[] response = readResponse();
        if(response[0].equals("ok")){
            // Registro l'esito positivo del login
            logged = true;
            ClientMain.username = username;
            // Registro il client per la ricezione delle callback
            server.registerCallback(username, notifyStub);

            // Se l'utente è membro di almeno un progetto,
            // preparo il necessario per utilizzare la chat
            for(int i=2;i<response.length;i++){
                // response[i] = [projectName,multicastIP,multicastPort] (i>=2)
                // projectData[0] = projectName
                // projectData[1] = multicastIP
                // projectData[2] = multicastPort
                String[] projectData = response[i].substring(1,response[i].length()-1).split(",");

                // Creo il "buffer" della chat
                ConcurrentLinkedQueue<String> messagesQueue = new ConcurrentLinkedQueue<>();
                // e lo aggiungo alla map <chats>
                chats.put(projectData[0], messagesQueue);
                // Creo un nuovo thread chatListener inizializzato con i valori del progetto corrente (ip,port,buffer)
                ChatListener chatListener = new ChatListener(projectData[1],Integer.parseInt(projectData[2]),messagesQueue);
                // Creo il thread corrispondente
                Thread chatListenerThread = new Thread(chatListener);
                // Lo aggiungo alla lista di threads listener
                chatListeners.add(chatListenerThread);
                // Avvio il thread listener
                chatListenerThread.start();
                // Salvo inoltre un riferimento all'IP per il progetto projectName per
                // poter successivamente inviare messaggi sulla chat senza interrogare il server
                projectMulticastIP.put(projectData[0],chatListener);
            }
            // Stampo un messaggio di conferma
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
        String[] response = readResponse();
        if(response[0].equals("ok")){
            // Registro l'esito positivo del logout
            logged = false;
            username = "Guest";
            // Disiscrivo il client dalla ricezione delle callbacks
            server.unregisterCallback(username, notifyStub);
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
        String[] response = readResponse();
        if(response[0].equals("ok")){
            // Aggiorno le informazioni di multicast del progetto appena creato
            // response[2] = [projectName,multicastIP,multicastPort]
            // projectData[0] = projectName
            // projectData[1] = multicastIP
            // projectData[2] = multicastPort
            String[] projectData = response[2].substring(1,response[2].length()-1).split(",");
            // Creo il "buffer" della chat
            ConcurrentLinkedQueue<String> messagesQueue = new ConcurrentLinkedQueue<>();
            // e lo aggiungo alla map <chats>
            chats.put(projectData[0], messagesQueue);
            // Creo un nuovo thread chatListener inizializzato con i valori del progetto corrente (ip,port,buffer)
            ChatListener chatListener = new ChatListener(projectData[1],Integer.parseInt(projectData[2]),messagesQueue);
            // Creo il thread corrispondente
            Thread chatListenerThread = new Thread(chatListener);
            // Lo aggiungo alla lista di threads listener
            chatListeners.add(chatListenerThread);
            // Avvio il thread listener
            chatListenerThread.start();
            // Salvo inoltre un riferimento all'IP per il progetto projectName per
            // poter successivamente inviare messaggi sulla chat senza interrogare il server
            projectMulticastIP.put(projectData[0],chatListener);

            // Stampo un messaggio di conferma
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
        String[] response = readResponse();
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
        String[] response = readResponse();
        if(response[0].equals("ok")){
            System.out.println("These are the members of the project "+projectName+":");
            // Parso la lista inviata dal server
            String[] membersList = response[1].substring(1,response[1].length()-1).split(" ");
            for (String s : membersList) {
                if (s.charAt(s.length() - 1) == ',')
                    System.out.println("\t- " + s.substring(0, s.length() - 1));
                else // Last member
                    System.out.println("\t- " + s);
            }
        }else {
            //if(DEBUG) System.out.print("["+response[1]+"] ");
            System.out.println(response[2]);
        }
    }

    private void addCard(String projectName, String cardName, String cardDescription)
            throws IllegalArgumentException, IOException {
        // Controllo validità dei parametri
        if(projectName.isEmpty()) throw new IllegalArgumentException("projectName");
        if(cardName.isEmpty()) throw new IllegalArgumentException("cardName");
        if(cardDescription.isEmpty()) throw new IllegalArgumentException("cardDescription");

        sendRequest("addCard "+username+" "+projectName+" "+cardName+" "+cardDescription);
        String[] response = readResponse();
        if(response[0].equals("ok")){
            System.out.println(response[1]);
        }else {
            //if(DEBUG) System.out.print("["+response[1]+"] ");
            System.out.println(response[2]);
        }

    }

    private void showCard(String projectName, String cardName)
            throws IllegalArgumentException, IOException {
        // Controllo validità dei parametri
        if(projectName.isEmpty()) throw new IllegalArgumentException("projectName");
        if(cardName.isEmpty()) throw new IllegalArgumentException("cardName");

        sendRequest("showCard "+username+" "+projectName+" "+cardName);
        String[] response = readResponse();
        if(response[0].equals("ok")){
            System.out.println("Some information about the "+cardName+" card:");
            System.out.println(response[1]);
        }else {
            //if(DEBUG) System.out.print("["+response[1]+"] ");
            System.out.println(response[2]);
        }
    }

    private void listProjects() throws IOException {
        sendRequest("listProjects "+username);
        String[] response = readResponse();
        if(response[0].equals("ok")){
            System.out.println("These are the projects you are a member of:");
            // Parso la lista inviata dal server
            String[] projectsList = response[1].substring(1,response[1].length()-1).split(" ");
            for (String s : projectsList) {
                if (s.charAt(s.length() - 1) == ',')
                    System.out.println("\t- " + s.substring(0, s.length() - 1));
                else // Last member
                    System.out.println("\t- " + s);
            }
        }else {
            //if(DEBUG) System.out.print("["+response[1]+"] ");
            System.out.println(response[2]);
        }
    }

    private void showCards(String projectName, boolean table)
            throws IOException, IllegalArgumentException {
        // Controllo validità dei parametri
        if(projectName.isEmpty()) throw new IllegalArgumentException("projectName");
        sendRequest("showCards "+username+" "+projectName);
        String[] response = readResponse();
        if(response[0].equals("ok")){
            System.out.println("Showcase of "+projectName+":");

            // response[1] = TODO
            // response[2] = INPROGRESS
            // response[3] = TOBEREVISED
            // response[4] = DONE

            String[] todo        = response[1].substring(1,response[1].length()-1).split(", ");
            String[] inprogress  = response[2].substring(1,response[2].length()-1).split(", ");
            String[] toberevised = response[3].substring(1,response[3].length()-1).split(", ");
            String[] done        = response[4].substring(1,response[4].length()-1).split(", ");

            if(table){
                // Table header
                System.out.format("|          %s         |       %s      |      %s      |          %s         |%n",
                                  "TODO","INPROGRESS","TOBERESIVED","DONE");
                int maxLength = Math.max(todo.length,Math.max(inprogress.length,Math.max(toberevised.length, done.length)));
                for(int i=0;i<maxLength;i++){
                    // TODO
                    try{ System.out.format("| %-22.21s",todo[i]);}
                    catch (ArrayIndexOutOfBoundsException ignored){System.out.format("|%-23.21s","");}

                    // INPROGRESS
                    try{ System.out.format("| %-22.21s",inprogress[i]);}
                    catch (ArrayIndexOutOfBoundsException ignored){System.out.format("|%-23.21s","");}

                    // TOBEREVISED
                    try{ System.out.format("| %-22.21s",toberevised[i]);}
                    catch (ArrayIndexOutOfBoundsException ignored){System.out.format("|%-23.21s","");}

                    // DONE
                    try{ System.out.format("| %-22.21s|",done[i]);}
                    catch (ArrayIndexOutOfBoundsException ignored){System.out.format("|%-23.21s|","");}

                    // NEWLINE
                    System.out.format("%n");
                }
            }else{
                System.out.println("TODO:");
                for(String todoCard : todo) System.out.println("\t> "+todoCard);
                System.out.println("INPROGRESS:");
                for(String inProgressCard : inprogress) System.out.println("\t> "+inProgressCard);
                System.out.println("TOBEREVISED:");
                for(String toBeRevisedCard : toberevised) System.out.println("\t> "+toBeRevisedCard);
                System.out.println("DONE:");
                for(String doneCard : done) System.out.println("\t> "+doneCard);
            }

        }else {
            //if(DEBUG) System.out.print("["+response[1]+"] ");
            System.out.println(response[2]);
        }
    }

    private void moveCard(String projectName, String cardName, String from, String to)
            throws IOException {
        if(projectName.isEmpty()) throw new IllegalArgumentException("projectName");
        if(cardName.isEmpty()) throw new IllegalArgumentException("cardName");
        if(from.isEmpty()) throw new IllegalArgumentException("from");
        if(to.isEmpty()) throw new IllegalArgumentException("to");

        sendRequest("moveCard "+username+" "+projectName+" "+cardName+" "+from+" "+to);
        String[] response = readResponse();
        if(response[0].equals("ok")){
            System.out.println(response[1]);
        }else {
            //if(DEBUG) System.out.print("["+response[1]+"] ");
            System.out.println(response[2]);
        }
    }

    private void getCardHistory(String projectName, String cardName) throws IOException {
        if(projectName.isEmpty()) throw new IllegalArgumentException("projectName");
        if(cardName.isEmpty()) throw new IllegalArgumentException("cardName");
        sendRequest("getCardHistory "+username+" "+projectName+" "+cardName);
        String[] response = readResponse();
        if(response[0].equals("ok")){
            System.out.println("The history of the movements of the card "+cardName+" is:");
            System.out.println("\t"+response[1].substring(0,response[1].length()-1).replaceAll("\\|"," -> "));
        }else {
            //if(DEBUG) System.out.print("["+response[1]+"] ");
            System.out.println(response[2]);
        }

    }

    private void cancelProject(String projectName)
            throws IOException,IllegalArgumentException {
        if(projectName.isEmpty()) throw new IllegalArgumentException("projectName");
        sendRequest("cancelProject "+username+" "+projectName);
        String[] response = readResponse();
        if(response[0].equals("ok")){
            System.out.println(response[1]);
        }else {
            //if(DEBUG) System.out.print("["+response[1]+"] ");
            System.out.println(response[2]);
        }
    }

    private void readChat(String projectName)
            throws ProjectNotFoundException {
        if(projectName.isEmpty()) throw new IllegalArgumentException("projectName");

        if(!chats.containsKey(projectName)) throw new ProjectNotFoundException(projectName);
        ConcurrentLinkedQueue<String> messagesQueue = chats.get(projectName);

        if(messagesQueue.size() == 0)
            System.out.println("No new message on this chat");
        else
            while(!messagesQueue.isEmpty())
                System.out.println(messagesQueue.poll());
    }

    private void sendChatMsg(String projectName, String message)
            throws ProjectNotFoundException {
        if(projectName.isEmpty()) throw new IllegalArgumentException("projectName");
        if(message.isEmpty()) throw new IllegalArgumentException("message");
        if(!chats.containsKey(projectName)) throw new ProjectNotFoundException(projectName);

        // (HH:MM) username: message
        String formattedMessage = getTime()+" "+username+": "+message;
        try {
            ChatListener multicastInfo = projectMulticastIP.get(projectName);
            multicastSocket.send(new DatagramPacket(
                    formattedMessage.getBytes(StandardCharsets.UTF_8), formattedMessage.length(),
                    InetAddress.getByName(multicastInfo.getMulticastIP()), multicastInfo.getMulticastPort()));
            System.out.println("Message sent!");
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    // * UTILS
    private void sendRequest(String request) throws IOException {
        socketChannel.write(ByteBuffer.wrap((request).getBytes(StandardCharsets.UTF_8)));
    }

    private String[] readResponse() throws IOException {
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
        return serverResponse.toString().split(":");
    }

    private String getTime(){
        Calendar now = Calendar.getInstance();
        return String.format("(%02d:%02d)",
                now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE));
    }

    @Override
    public void notifyProject(String multicastInfo) throws RemoteException {
        // L'utente è stato aggiunto ad un nuovo progetto e deve
        // sincronizzare le proprie informazioni multicast per
        // partecipare alla chat di progetto

        // Aggiorno le informazioni di multicast del progetto appena creato
        // response[2] = [projectName,multicastIP,multicastPort]
        // projectData[0] = projectName
        // projectData[1] = multicastIP
        // projectData[2] = multicastPort
        String[] projectData = multicastInfo.substring(1,multicastInfo.length()-1).split(",");
        // Creo il "buffer" della chat
        ConcurrentLinkedQueue<String> messagesQueue = new ConcurrentLinkedQueue<>();
        // e lo aggiungo alla map <chats>
        chats.put(projectData[0], messagesQueue);
        // Creo un nuovo thread chatListener inizializzato con i valori del progetto corrente (ip,port,buffer)
        ChatListener chatListener = new ChatListener(projectData[1],Integer.parseInt(projectData[2]),messagesQueue);
        // Creo il thread corrispondente
        Thread chatListenerThread = new Thread(chatListener);
        // Lo aggiungo alla lista di threads listener
        chatListeners.add(chatListenerThread);
        // Avvio il thread listener
        chatListenerThread.start();
        // Salvo inoltre un riferimento all'IP per il progetto projectName per
        // poter successivamente inviare messaggi sulla chat senza interrogare il server
        projectMulticastIP.put(projectData[0],chatListener);

        System.out.println("\nAggiunto ad un nuovo progetto"+projectData[0]);
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
