// @author Luca Cirillo (545480)

import WorthExceptions.*;
import com.google.gson.Gson;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.ProviderMismatchException;
import java.rmi.RemoteException;
import java.net.InetSocketAddress;
import java.rmi.registry.Registry;
import java.rmi.server.RemoteObject;
import java.rmi.AlreadyBoundException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("ConstantConditions")
public class ServerMain extends RemoteObject implements Server, ServerRMI{

    // * TCP
    private static final int PORT_TCP = 6789;
    // * RMI
    private static final int PORT_RMI = 9876;
    private static final String NAME_RMI = "WORTH-SERVER";
    // * SERVER
    private final boolean DEBUG = true;
    private final List<User> users;
    private final List<PublicUser> publicUsers;
    private final List<NotifyEventInterface> clients;
    private final Map<String, Project> projects;
    private static final Gson gson = new Gson();

    public ServerMain(){
        // Callback
        super();
        clients = new ArrayList<>();

        // Persistenza
        this.users = new ArrayList<>();
        this.publicUsers = new ArrayList<>();
        this.projects = new ConcurrentHashMap<>();
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

        // * LIVE
        while (true) {
            try {
                // Seleziona un insieme di keys che corrispondono a canali pronti ad eseguire operazioni
                Thread.sleep(300); // Limita overhead mentre debuggo
                selector.select();
            } catch (IOException ioe) { ioe.printStackTrace(); break;
            } catch (InterruptedException e) { e.printStackTrace(); }

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
                        if(DEBUG) System.out.println("<" + client.getRemoteAddress() + ">: TCP Accepted");
                        client.configureBlocking(false);
                        // Registro il nuovo client sul Selector
                        client.register(selector, SelectionKey.OP_READ, null);
                    }

                    // Un canale pronto ad un'operazione di lettura
                    else if (key.isReadable()) {
                        SocketChannel client = (SocketChannel) key.channel();
                        // Recupero la richiesta del client
                        String  request = readRequest(client);
                        // Divido il comando e gli eventuali argomenti
                        // (Con la .trim() mi assicuro di eliminare spazi vuoti "inutili"
                        String[] cmd = request.trim().split(" ");

                        if (cmd.length > 0) {
                            switch (cmd[0]) { // Seleziono il comando

                                case "login":
                                    try {
                                        login(cmd[1], cmd[2]);
                                        key.attach("ok");
                                    } catch (UserNotFoundException e) {
                                        key.attach("ko:404:User not found");
                                    } catch (AuthFailException e) {
                                        key.attach("ko:401:Wrong password");
                                    }
                                    break;

                                case "logout":
                                    try{
                                        logout(cmd[1]);
                                        key.attach("ok");
                                    } catch (UserNotFoundException e) {
                                        // In teoria, non può succedere per via dell'implementazione del client
                                        key.attach("ko:404:User not found");
                                    }
                                    break;

                                case "createProject":
                                    try{
                                        createProject(cmd[1],cmd[2]);
                                        key.attach("ok");
                                    } catch (ProjectNameAlreadyInUse e) {
                                        key.attach("ko:409:Project name already in use");
                                    }
                                    break;

                                case "addMember":
                                    try{
                                        addMember(cmd[1],cmd[2],cmd[3]);
                                        key.attach("ok");
                                    } catch (ProjectNotFoundException e) {
                                        key.attach("ko:404:Project not found");
                                    } catch (UserNotFoundException e) {
                                        key.attach("ko:404:User not found");
                                    } catch (AlreadyMemberException e) {
                                        key.attach("ko:409:Already member");
                                    }
                                    break;

                                case "showMembers":
                                    try{
                                        key.attach("ok:"+showMembers(cmd[1], cmd[2]));
                                    } catch (ForbiddenException e) {
                                        key.attach("ko:403:You're not member of this project");
                                    } catch (ProjectNotFoundException e) {
                                        key.attach("ko:404:Project not found");
                                    }
                                    break;

                                case "addCard":
                                    try{
                                        addCard(cmd[1],cmd[2],cmd[3],cmd[4]);
                                        key.attach("ok");
                                    } catch (ForbiddenException e) {
                                        key.attach("ko:403:You're not member of this project");
                                    } catch (ProjectNotFoundException e) {
                                        key.attach("ko:404:Project not found");
                                    } catch (CardAlreadyExists cardAlreadyExists) {
                                        key.attach("ko:409:Card name already in use");
                                    }
                                    break;
                            }
                        }

                        key.interestOps(SelectionKey.OP_WRITE);
                    }

                    // Un canale pronto per un'operazione di scrittura
                    else if (key.isWritable()) {
                        SocketChannel client = (SocketChannel) key.channel();
                        String msg = (String) key.attachment();
                        if (msg == null) {
                            // Client disconnesso ?
                            key.cancel();
                            key.channel().close();
                        } else {
                            // Invio di una risposta
                            client.write(ByteBuffer.wrap(msg.getBytes(StandardCharsets.UTF_8)));
                            key.attach(null);
                            key.interestOps(SelectionKey.OP_READ);
                        }
                    }
                } catch (IOException e) { e.printStackTrace(); }
            }
        }
    }

    // * ENDPOINTS

    // Registra un nuovo utente nel sistema
    public void register(String username, String password)
            throws RemoteException, IllegalArgumentException, UsernameAlreadyTakenException {

        // Controllo validità dei parametri
        if(username.isEmpty()) throw new IllegalArgumentException("username");
        if(password.isEmpty()) throw new IllegalArgumentException("password");
        if(DEBUG) System.out.println("Server@WORTH > register "+username+" "+password);

        // Controllo unicità username
        if(userExists(username)) throw new UsernameAlreadyTakenException("username");

        // Registro utente nel database
        users.add(new User(username, password));
        publicUsers.add(new PublicUser(username));
    }

    // Permette ad un utente di utilizzare il sistema
    private void login(String username, String password)
            throws IllegalArgumentException, UserNotFoundException, AuthFailException {
        // Controllo validità dei parametri
        if(username.isEmpty()) throw new IllegalArgumentException("username");
        if(password.isEmpty()) throw new IllegalArgumentException("password");

        // Controllo l'esistenza dell'utente
        if(!userExists(username)) throw new UserNotFoundException(username);

        // Controllo se la password è corretta
        if(!getUser(username).auth(password)) throw new AuthFailException(password);

        // Aggiorno il suo stato su Online
        getUser(username).setStatus(User.Status.ONLINE);
        getPublicUser(username).setStatus(User.Status.ONLINE);

        // Aggiorno tutti gli altri clients con una callback
        try { sendCallbacks(); }
        catch (RemoteException e) { e.printStackTrace(); }
    }

    // Utente abbandona correttamente il sistema
    private void logout(String username)
            throws IllegalArgumentException, UserNotFoundException {
        // Controllo validità dei parametri
        if(username.isEmpty()) throw new IllegalArgumentException("username");

        // Controllo l'esistenza dell'utente
        if(!userExists(username)) throw new UserNotFoundException(username);

        // Limito l'overhead non facendo controlli in quanto, se arriva un comando di logout
        // sicuramente l'utente ha fatto un login precedentemente, per come il client è implementato
        // Aggiorno il suo stato su Offline
        getUser(username).setStatus(User.Status.OFFLINE);
        getPublicUser(username).setStatus(User.Status.OFFLINE);

        // Aggiorno tutti gli altri clients con una callback
        try { sendCallbacks(); }
        catch (RemoteException e) {e.printStackTrace();}
    }

    // Utente richiede la creazione di un nuovo progetto
    private void createProject(String username, String projectName)
            throws IllegalArgumentException, ProjectNameAlreadyInUse {
        // Controllo validità dei parametri
        if(username.isEmpty()) throw new IllegalArgumentException("username");
        if(projectName.isEmpty()) throw new IllegalArgumentException("projectName");

        // Controllare che non esista già un progetto con lo stesso nome
        if(projects.containsKey(projectName)) throw new ProjectNameAlreadyInUse(projectName);

        // Creo un nuovo progetto e lo aggiungo al database
        projects.put(projectName, new Project(projectName, getUser(username)));
    }

    private void addMember(String username, String projectName, String memberUsername)
            throws IllegalArgumentException, ProjectNotFoundException, UserNotFoundException, AlreadyMemberException {
        // Controllo validità dei parametri
        if(username.isEmpty()) throw new IllegalArgumentException("username");
        if(projectName.isEmpty()) throw new IllegalArgumentException("projectName");
        if(memberUsername.isEmpty()) throw new IllegalArgumentException("memberUsername");

        // Controllo se esiste un progetto con il nome indicato
        if(!projects.containsKey(projectName)) throw new ProjectNotFoundException(projectName);

        // Controllo che memberUsername sia effettivamente un utente del sistema
        if(!userExists(memberUsername)) throw new UserNotFoundException(memberUsername);

        Project project = projects.get(projectName);

        // Controllo che memberUsername non faccia ancora parte di projectName
        if(project.getMembers().contains(getUser(memberUsername)))
            throw new AlreadyMemberException(memberUsername);

        // Aggiungo memberUsername come nuovo membro del progetto projectName
        project.addMember(getUser(memberUsername));

    }

    private String showMembers(String username, String projectName)
            throws IllegalArgumentException, ProjectNotFoundException, ForbiddenException {
        // Controllo validità dei parametri
        if(username.isEmpty()) throw new IllegalArgumentException("username");
        if(projectName.isEmpty()) throw new IllegalArgumentException("projectName");

        // Controllo se esiste un progetto con il nome indicato
        if(!projects.containsKey(projectName)) throw new ProjectNotFoundException(projectName);

        Project project = projects.get(projectName);
        // Se l'utente è un membro del progetto, può consultare la lista membri
        if(project.getMembers().contains(getUser(username)))
            return project.getMembers().toString();
        else throw new ForbiddenException();
    }

    private void addCard(String username, String projectName, String cardName, String cardCaption)
            throws ProjectNotFoundException, ForbiddenException, CardAlreadyExists {
        // Controllare che il progetto esista
        if(!projects.containsKey(projectName)) throw new ProjectNotFoundException(projectName);
        Project project = projects.get(projectName);
        // Controllare che l'utente sia un membro del progetto
        if(!project.getMembers().contains(getUser(username))) throw new ForbiddenException();
        // Controllare che non ci sia già una card con lo stesso nome
        if(project.getCard(cardName) != null) throw new CardAlreadyExists(cardName);
        // Creare la nuova card
        project.addCard(cardName,cardCaption);

    }

    // * UTILS
    // Legge la richiesta inviata dal client
    private String readRequest(SocketChannel client) throws IOException {
        // Alloco un buffer di <DIM_BUFFER>
        ByteBuffer msgBuffer = ByteBuffer.allocate(1024);
        // Stringa corrispondente alla risposta del server
        StringBuilder clientRequest = new StringBuilder();
        // Quantità di bytes letti ad ogni chiamata della .read()
        int bytesRead;

        do {
            // Svuoto il buffer
            msgBuffer.clear();
            // Leggo dal canale direttamente nel buffer
            // e mi salvo la quantità di bytes letti
            bytesRead = client.read(msgBuffer);
            // Passo il buffer dalla modalità lettura a scrittura
            msgBuffer.flip();
            // Costruisco la risposta del server appendendoci
            // la stringa letta (finora) dal canale
            clientRequest.append(StandardCharsets.UTF_8.decode(msgBuffer).toString());
            // Riporto il buffer in modalità lettura
            msgBuffer.flip();

            // Finché ci sono bytes da leggere, continuo
        } while (bytesRead >= 1024);

        if(DEBUG) System.out.println("Server@WORTH > "+clientRequest);
        return clientRequest.toString();
    }

    // Controlla l'esistenza di un utente nel sistema
    private boolean userExists(String username){
        return users.stream().anyMatch(user -> user.getUsername().equals(username));
    }

    // Recupera l'utente <username>
    private User getUser(String username){
        for(User user: users)
            if(user.getUsername().equals(username)) return user;
        return null;
    }

    private PublicUser getPublicUser(String username){
        for(PublicUser user: publicUsers)
            if(user.getUsername().equals(username)) return user;
        return null;
    }

    // * CALLBACKS
    @Override
    public synchronized void registerCallback(NotifyEventInterface clientInterface)
            throws RemoteException {
        if(!clients.contains(clientInterface)) {
            clients.add(clientInterface);
            clientInterface.notifyEvent(publicUsers);
        }
    }

    @Override
    public synchronized void unregisterCallback(NotifyEventInterface clientInterface) throws RemoteException {
        clients.remove(clientInterface);
    }

    public synchronized void sendCallbacks() throws RemoteException{
        for (NotifyEventInterface client : clients)
            client.notifyEvent(publicUsers);
    }

    // * MAIN
    public static void main(String[] args){
        ServerMain server = new ServerMain();
        server.live();
    }
}
