// @author Luca Cirillo (545480)

import WorthExceptions.*;
import com.google.gson.*;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.rmi.registry.Registry;
import java.rmi.server.RemoteObject;
import java.rmi.AlreadyBoundException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@SuppressWarnings("ReadWriteStringCanBeUsed")
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
    //private final List<NotifyEventInterface> clients;
    private final Map<String, NotifyEventInterface> clients;
    private final Map<String, Project> projects;
    private static final Gson gson = new Gson();
    Random random = new Random();
    // * PATHS
    private static final Path pathMulticast = Paths.get("Multicast.json");

    public ServerMain(){
        // Callback
        super();
        clients = new LinkedHashMap<>();
        // Multicast
        String defaultMulticast = """
            {"lastMulticastIP":"224.0.0.1","releasedIP":[]}
        """;
        if(!Files.exists(pathMulticast)){
            try {
                Files.write(pathMulticast,defaultMulticast.getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                System.err.println("Failed to create default Multicast config file!");
                System.exit(-1); }
        }

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
                        String request = readRequest(client);
                        // Divido il comando e gli eventuali argomenti
                        // (Con la .trim() mi assicuro di eliminare spazi vuoti "inutili"
                        String[] cmd = request.trim().split(" ");

                        if (cmd.length > 0) {
                            switch (cmd[0]) { // Seleziono il comando

                                case "login":
                                    try {
                                        // cmd[1] = username, nome utente del proprio account
                                        // cmd[2] = password, password del proprio account
                                        key.attach("ok:Great! Now your are logged as "+cmd[1]+"!:"+login(cmd[1], cmd[2]));
                                    } catch (AuthFailException e) {
                                        key.attach("ko:401:The password you entered is incorrect, please try again");
                                    } catch (UserNotFoundException e) {
                                        key.attach("ko:404:Are you sure that an account with this name exists?\nIf you need one, use register command");
                                    }
                                    break;

                                case "logout":
                                    try{
                                        // cmd[1] = username, nome utente del proprio account
                                        logout(cmd[1]);
                                        key.attach("ok:You have been logged out successfully");
                                    } catch (UserNotFoundException e) {
                                        // In teoria, non può succedere per via di come il client è stato implementato
                                        key.attach("ko:404:Are you sure that an account with this name exists?\nIf you need one, use register command");
                                    }
                                    break;

                                case "createProject":
                                    try{
                                        // cmd[1] = username, nome utente del proprio account
                                        // cmd[2] = projectName, nome del progetto
                                        key.attach("ok:Project "+cmd[2]+" created!\nCurrently you're the only member. " +
                                                "Try use addMember to invite some coworkers:"
                                                +createProject(cmd[1],cmd[2]));
                                    } catch (ProjectNameAlreadyInUse e) {
                                        key.attach("ko:409:The name chosen for the project is already in use, try another one!");
                                    } catch (MulticastException e) {
                                        e.printStackTrace();
                                    }
                                    break;

                                case "addMember":
                                    try{
                                        // cmd[1] = username, nome utente del proprio account
                                        // cmd[2] = projectName, nome del progetto
                                        // cmd[3] = memberUsername, username dell'utente da inserire
                                        addMember(cmd[1],cmd[2],cmd[3]);
                                        key.attach("ok:"+cmd[3]+" added to "+cmd[2]+"!");
                                    } catch (ForbiddenException e) {
                                        key.attach("ko:403:You're not member of this project");
                                    } catch (ProjectNotFoundException e) {
                                        key.attach("ko:404:Can't found "+cmd[2]+", are you sure that exists? Try createProject to create a project");
                                    } catch (UserNotFoundException e) {
                                        key.attach("ko:404:Can't found an account named "+cmd[3]+"! Maybe is a typo?");
                                    } catch (AlreadyMemberException e) {
                                        key.attach("ko:409:" + cmd[3] + " is already a member of " + cmd[2]);
                                    } catch (RemoteException e){
                                        key.attach("ko:500:Project created, but failed to update local chat information. Please login and logout again to properly user project chat!");
                                    }
                                    break;

                                case "showMembers":
                                    try{
                                        // cmd[1] = username, nome utente del proprio account
                                        // cmd[2] = projectName, nome del progetto
                                        key.attach("ok:"+showMembers(cmd[1], cmd[2]));
                                    } catch (ForbiddenException e) {
                                        key.attach("ko:403:You're not member of this project");
                                    } catch (ProjectNotFoundException e) {
                                        key.attach("ko:404:Can't found "+cmd[2]+", are you sure that exists? Try createProject to create a project");
                                    }
                                    break;

                                case "addCard":
                                    try{
                                        // cmd[1] = username, nome utente del proprio account
                                        // cmd[2] = projectName, nome del progetto
                                        // cmd[3] = cardName, nome della card
                                        // cmd[4] = cardDescription, descrizione testuale della card
                                        addCard(cmd[1],cmd[2],cmd[3],cmd[4]);
                                        key.attach("ok:Card "+cmd[3]+" created");
                                    } catch (ForbiddenException e) {
                                        key.attach("ko:403:You're not member of this project");
                                    } catch (ProjectNotFoundException e) {
                                        key.attach("ko:404:Can't found "+cmd[2]+", are you sure that exists? Try createProject to create a project");
                                    } catch (CardAlreadyExists cardAlreadyExists) {
                                        key.attach("ko:409:A card with the same name already exists");
                                    }
                                    break;

                                case "showCard":
                                    try{
                                        // cmd[1] = username, nome utente del proprio account
                                        // cmd[2] = projectName, nome del progetto
                                        // cmd[3] = cardName, nome della card
                                        key.attach("ok:"+showCard(cmd[1],cmd[2],cmd[3]));
                                    } catch (ForbiddenException e) {
                                        key.attach("ko:403:You're not member of this project");
                                    } catch (ProjectNotFoundException e) {
                                        key.attach("ko:404:Can't found "+cmd[2]+", are you sure that exists? Try createProject to create a project");
                                    } catch (CardNotFoundException e) {
                                        key.attach("ko:404:Can't found "+cmd[3]+", are you sure that exists? Try addCard to create a card");
                                    }
                                    break;

                                case "showCards":
                                    try{
                                        // cmd[1] = username, nome utente del proprio account
                                        // cmd[2] = projectName, nome del progetto
                                        key.attach("ok:"+showCards(cmd[1],cmd[2]));
                                    } catch (ForbiddenException e) {
                                        key.attach("ko:403:You're not member of this project");
                                    } catch (ProjectNotFoundException e) {
                                        key.attach("ko:404:Can't found "+cmd[2]+", are you sure that exists? Try createProject to create a project");
                                    }
                                    break;

                                case "moveCard":
                                    try{
                                        // cmd[1] = username, nome utente del proprio account
                                        // cmd[2] = projectName, nome del progetto
                                        // cmd[3] = cardName, nome della card
                                        // cmd[4] = from, lista dove si trova attualmente la card
                                        // cmd[5] = to, lista in cui si desidera spostare la card
                                        moveCard(cmd[1],cmd[2],cmd[3],cmd[4],cmd[5]);
                                        key.attach("ok:"+cmd[3]+" moved from "+cmd[4]+" to "+cmd[5]);
                                    } catch (IllegalCardMovementException e) {
                                        key.attach("ko:406:You can't move a card from "+cmd[4]+" to "+cmd[5]);
                                    } catch (ForbiddenException e) {
                                        key.attach("ko:403:You're not member of this project");
                                    } catch (CardNotFoundException e) {
                                        key.attach("ko:404:Can't found "+cmd[3]+", are you sure that exists? Try addCard to create a card");
                                    } catch (ProjectNotFoundException e) {
                                        key.attach("ko:404:Can't found "+cmd[2]+", are you sure that exists? Try createProject to create a project");
                                    }
                                    break;

                                case "getCardHistory":
                                    try{
                                        // cmd[1] = username, nome utente del proprio account
                                        // cmd[2] = projectName, nome del progetto
                                        // cmd[3] = cardName, nome della card
                                        key.attach("ok:"+getCardHistory(cmd[1],cmd[2],cmd[3]));
                                    } catch (ForbiddenException e) {
                                        key.attach("ko:403:You're not member of this project");
                                    } catch (ProjectNotFoundException e) {
                                        key.attach("ko:404:Can't found "+cmd[2]+", are you sure that exists? Try createProject to create a project");
                                    } catch (CardNotFoundException e) {
                                        key.attach("ko:404:Can't found "+cmd[3]+", are you sure that exists? Try addCard to create a card");
                                    }
                                    break;

                                case "listProjects":
                                    try{
                                        // cmd[1] = username, nome utente del proprio account
                                        key.attach("ok:"+listProjects(cmd[1]));
                                    } catch (ProjectNotFoundException e) {
                                        key.attach("ko:404:You are not a member of any project, yet");
                                    }
                                    break;

                                case "cancelProject":
                                    try{
                                        cancelProject(cmd[1],cmd[2]);
                                        key.attach("ok:Project "+cmd[2]+" was canceled");
                                    } catch (ForbiddenException e) {
                                        key.attach("ko:403:You're not member of this project");
                                    } catch (PendingCardsException e) {
                                        key.attach("ko:412:Not all cards are in the done list. There is still work to be done!");
                                    } catch (ProjectNotFoundException e) {
                                        key.attach("ko:404:Can't found "+cmd[2]+", are you sure that exists? Try createProject to create a project");
                                    }

                            }
                        }
                        key.interestOps(SelectionKey.OP_WRITE);
                    }

                    // Un canale pronto per un'operazione di scrittura
                    else if (key.isWritable()) {
                        SocketChannel client = (SocketChannel) key.channel();
                        String msg = (String) key.attachment();

                        if (msg == null) {
                            // Client disconnesso
                            System.out.println(client.getRemoteAddress() + ": TCP Close");
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
        //publicUsers.add(new PublicUser(username));
    }

    // Permette ad un utente di utilizzare il sistema
    private String login(String username, String password)
            throws UserNotFoundException, AuthFailException {
        if(DEBUG) System.out.println("Server@WORTH > login "+username+" "+password);

        // Controllo l'esistenza dell'utente
        if(!userExists(username)) throw new UserNotFoundException(username);

        // Recupero le informazioni dell'utente
        User user = getUser(username);

        // Controllo se la password è corretta
        if(!user.auth(password)) throw new AuthFailException(password);

        // Aggiorno il suo stato su Online
        user.setStatus(User.Status.ONLINE);
        //getPublicUser(username).setStatus(User.Status.ONLINE);

        // Aggiorno tutti gli altri clients con una callback
        // per informarli che l'utente è online
        try { sendCallback(); }
        catch (RemoteException e) { e.printStackTrace(); }

        // Ritorno le informazioni Multicast su tutti i
        // (eventuali) progetti di cui l'utente è membro
        List<Project> userProjects = user.getProjects();
        // Se l'utente non è membro di nessun progetto, ritorno una stringa vuota
        if(userProjects.isEmpty()) return "";
        StringBuilder output = new StringBuilder();
        for(Project project : userProjects) output.append(project.getMulticastInfo());
        return output.toString();

    }

    // Utente abbandona correttamente il sistema
    private void logout(String username)
            throws UserNotFoundException {

        if(DEBUG) System.out.println("Server@WORTH > logout "+username);

        // Controllo l'esistenza dell'utente
        if(!userExists(username)) throw new UserNotFoundException(username);

        // Limito l'overhead non facendo controlli in quanto, se arriva un comando di logout
        // sicuramente l'utente ha fatto un login precedentemente, per come il client è implementato
        // Aggiorno il suo stato su Offline
        getUser(username).setStatus(User.Status.OFFLINE);
        //getPublicUser(username).setStatus(User.Status.OFFLINE);

        // Aggiorno tutti gli altri clients con una callback
        // per informarli che l'utente è ora offline
        try { sendCallback(); }
        catch (RemoteException e) {e.printStackTrace();}
    }

    // Utente richiede la creazione di un nuovo progetto
    private String createProject(String username, String projectName)
            throws ProjectNameAlreadyInUse, MulticastException { // TODO usare un altra eccezione per il multicast error

        if(DEBUG) System.out.println("Server@WORTH > createProject "+username+" "+projectName);

        // Controllare che non esista già un progetto con lo stesso nome
        if(projects.containsKey(projectName)) throw new ProjectNameAlreadyInUse(projectName);

        // Creo un nuovo progetto e lo aggiungo al database
        User user = getUser(username);
        Project project = new Project(projectName, user, genMulticastIP(),1025+random.nextInt(64510));
        projects.put(projectName, project);
        user.addProject(project);
        // Ritorno le informazioni multicast da passare all'utente
        // affinché possa utilizzare la chat di progetto
        return project.getMulticastInfo();
    }

    private void addMember(String username, String projectName, String memberUsername)
            throws ProjectNotFoundException, UserNotFoundException, AlreadyMemberException, ForbiddenException, RemoteException {

        if(DEBUG) System.out.println("Server@WORTH > addMember "+username+" "+projectName+" "+memberUsername);

        // Controllo se esiste un progetto con il nome indicato
        if(!projects.containsKey(projectName)) throw new ProjectNotFoundException(projectName);
        // Controllo che memberUsername sia effettivamente un utente del sistema
        if(!userExists(memberUsername)) throw new UserNotFoundException(memberUsername);

        Project project = projects.get(projectName);
        User user = getUser(memberUsername);

        // controllo che username sia un membro di projectName
        if(!project.getMembers().contains(getUser(username))) throw new ForbiddenException();

        // Controllo che memberUsername non faccia già parte di projectName
        if(project.getMembers().contains(user))
            throw new AlreadyMemberException(memberUsername);

        // Aggiungo memberUsername come nuovo membro del progetto projectName
        project.addMember(user);
        user.addProject(project);

        clients.get(memberUsername).notifyProject(project.getMulticastInfo(), username);
        // TODO: inviare una callback all'utente aggiunto per trasmettere
        // i dati multicast relativi al progetto

    }

    private String showMembers(String username, String projectName)
            throws ProjectNotFoundException, ForbiddenException {

        if(DEBUG) System.out.println("Server@WORTH > showMembers "+username+" "+projectName);

        // Controllo se esiste un progetto con il nome indicato
        if(!projects.containsKey(projectName)) throw new ProjectNotFoundException(projectName);

        Project project = projects.get(projectName);
        // Se l'utente è un membro del progetto, può consultare la lista membri
        if(project.getMembers().contains(getUser(username)))
            return project.getMembers().stream()
                .map(User::getUsername)
                .collect(Collectors.toList()).toString();
        else throw new ForbiddenException();
    }

    private void addCard(String username, String projectName, String cardName, String cardDescription)
            throws ProjectNotFoundException, ForbiddenException, CardAlreadyExists {

        if(DEBUG) System.out.println("Server@WORTH > addCard "+username+" "+projectName+" "+cardName+" "+cardDescription);

        // Controllare che il progetto esista
        if(!projects.containsKey(projectName)) throw new ProjectNotFoundException(projectName);
        Project project = projects.get(projectName);

        // Controllare che l'utente sia un membro del progetto
        if(!project.getMembers().contains(getUser(username))) throw new ForbiddenException();
        // Controllare che non ci sia già una card con lo stesso nome
        if(project.getCard(cardName) != null) throw new CardAlreadyExists(cardName);
        // Creare la nuova card
        project.addCard(cardName,cardDescription);

    }

    private String showCard(String username, String projectName, String cardName)
            throws CardNotFoundException, ForbiddenException, ProjectNotFoundException {
        if(DEBUG) System.out.println("Server@WORTH > showCard "+username+" "+projectName+" "+cardName);

        // Controllare che il progetto esista
        if(!projects.containsKey(projectName)) throw new ProjectNotFoundException(projectName);
        Project project = projects.get(projectName);

        // Controllare che l'utente sia un membro del progetto
        if(!project.getMembers().contains(getUser(username))) throw new ForbiddenException();

        Card card = project.getCard(cardName);
        if(card == null) throw new CardNotFoundException(cardName);
        return card.toString();
    }

    private String listProjects(String username)
            throws ProjectNotFoundException {
        if(DEBUG) System.out.println("Server@WORTH > listProjects "+username);

        List<Project> list = getUser(username).getProjects();
        if(list.size() == 0) throw new ProjectNotFoundException(username);
        return list.stream()
                .map(Project::getName)
                .collect(Collectors.toList()).toString();
    }

    private String showCards(String username, String projectName)
            throws ProjectNotFoundException, ForbiddenException {
        if(DEBUG) System.out.println("Server@WORTH > showCards "+username+" "+projectName);

        // Controllare che il progetto esista
        if(!projects.containsKey(projectName)) throw new ProjectNotFoundException(projectName);
        Project project = projects.get(projectName);

        // Controllare che l'utente sia un membro del progetto
        if(!project.getMembers().contains(getUser(username))) throw new ForbiddenException();

        // Costruisco una stringa contente i nomi di tutte le card
        // Organizzate come list.toString(), ovvero
        // [c1,c2]:[c3]:[c4,c5]:[c6]
        StringBuilder output = new StringBuilder();
        for(Project.Section section : Project.Section.values()){
            output.append(project.getList(section).stream()
                    .map(Card::getName).collect(Collectors.toList()).toString()).append(":");
        }

        // Substring per rimuovere l'ultimo : inserito
        return output.substring(0,output.length()-1);
    }

    private void moveCard(String username, String projectName, String cardName, String from, String to)
            throws ProjectNotFoundException, ForbiddenException, CardNotFoundException, IllegalCardMovementException {
        // Controllare che il progetto esista
        if(!projects.containsKey(projectName)) throw new ProjectNotFoundException(projectName);
        Project project = projects.get(projectName);

        // Controllare che l'utente sia un membro del progetto
        if(!project.getMembers().contains(getUser(username))) throw new ForbiddenException();

        // Controllo che esista la card
        if(project.getCard(cardName) == null) throw new CardNotFoundException(cardName);

        // Controllo se from e to sono liste valide
        // try/catch ? what if different string
        Project.Section fromSection = Project.Section.valueOf(from.toUpperCase());
        Project.Section toSection   = Project.Section.valueOf(to.toUpperCase());

        // Sposto la card (il rispetto dei vincoli è assicurato dal metodo invocato
        project.moveCard(cardName,fromSection,toSection);

        // Invio una notifica sulla chat di progetto
        String message = getTime()+" WORTH: "+username+" has moved card "+cardName+
                " from "+fromSection.toString() +" to "+toSection.toString();
        try {
            new DatagramSocket().send(new DatagramPacket(
                    message.getBytes(StandardCharsets.UTF_8), message.length(),
                    InetAddress.getByName(project.getMulticastIP()), project.getMulticastPort()));
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private String getTime(){
        Calendar now = Calendar.getInstance();
        return String.format("(%02d:%02d)",
                now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE));
    }

    private String getCardHistory(String username, String projectName, String cardName)
            throws CardNotFoundException, ForbiddenException, ProjectNotFoundException {
        // Controllare che il progetto esista
        if(!projects.containsKey(projectName)) throw new ProjectNotFoundException(projectName);
        Project project = projects.get(projectName);

        // Controllare che l'utente sia un membro del progetto
        if(!project.getMembers().contains(getUser(username))) throw new ForbiddenException();

        // Controllo che esista la card
        if(project.getCard(cardName) == null) throw new CardNotFoundException(cardName);

        // Ritorno la history della card
        return project.getCard(cardName).getHistory();
    }

    private void cancelProject(String username, String projectName)
            throws ProjectNotFoundException, ForbiddenException, PendingCardsException {
        // Controllare che il progetto esista
        if(!projects.containsKey(projectName)) throw new ProjectNotFoundException(projectName);
        Project project = projects.get(projectName);

        // Controllare che l'utente sia un membro del progetto
        if(!project.getMembers().contains(getUser(username))) throw new ForbiddenException();

        // Controllo che tutte le cards siano nella lista DONE
        if(project.canDelete()){
            // Salvo l'IP del progetto per essere riutilizzato
            try{ saveReleasedIP(project.getMulticastIP());
            } catch (MulticastException ignored) {}
            // Rimuovo il progetto dalla lista personale dell'utente
            getUser(username).removeProject(project);
            // Rimuovo il progetto dalla lista globale
            projects.remove(projectName);
        }else throw new PendingCardsException();
    }
    private void saveReleasedIP(String IP) throws MulticastException {
        // Carico in memoria il file Multicast.json
        String multicast = null;
        try { multicast = new String(Files.readAllBytes(pathMulticast));
        } catch (IOException e) { throw new MulticastException(); }
        // Parso la stringa JSON con Gson
        JsonObject multicastJson = new JsonParser().parse(multicast).getAsJsonObject();
        // "Seleziono" la lista degli IP che si sono liberati
        JsonArray releasedIP = (JsonArray) multicastJson.get("releasedIP");
        // Aggiungo il nuovo IP appena rilasciato da un progetto
        releasedIP.add(IP);
        // Rimetto la lista nel JSON
        multicastJson.add("releasedIP",releasedIP);
        // Salvo tutto nuovamente su file
        try { Files.write(pathMulticast,multicastJson.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) { throw new MulticastException(); }
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

    private String genMulticastIP() throws MulticastException {
        // 224.0.0.1 -> 239.255.255.255
        String multicastIP = "";
        // Carico in memoria il file Multicast.json
        String multicast = null;
        try { multicast = new String(Files.readAllBytes(pathMulticast));
        } catch (IOException e) { throw new MulticastException(); }
        // Parso la stringa JSON con Gson
        JsonObject multicastJson = new JsonParser().parse(multicast).getAsJsonObject();
        // Se sono disponibili IP liberati da progetti cancellati
        JsonArray releasedIP = (JsonArray) multicastJson.get("releasedIP");
        if(releasedIP.size() > 0){
            // Prendo il più vecchio IP che si è liberato (il primo della lista)
            multicastIP = releasedIP.get(0).toString().replace("\"","");
            System.out.println(multicastIP);
            // Lo rimuovo dalla lista
            releasedIP.remove(0);
            // Rimetto la lista aggiornata nel JSON
            multicastJson.add("releasedIP",releasedIP);
        }else {
            // Altrimenti, genero un nuovo IP incrementale
            // Leggo l'ultimo IP utilizzato dal JSON come una stringa
            String sLastMulticastIP = multicastJson.get("lastMulticastIP").getAsString();
            // Controllo che non sia arrivato all'ultimo IP disponibile (239.255.255.255)
            // Nel caso, faccio un hard reset riportandolo al primo
            if (sLastMulticastIP.equals("239.255.255.255")) {
                multicastIP = "224.0.0.1";
            } else {
                // Divido gli ottetti separati dal punto (.)
                // e li casto da String a int
                int[] lastMulticastIP = Arrays.stream(
                        sLastMulticastIP.split("\\."))
                        .mapToInt(Integer::parseInt).toArray();
                // Li parso e li incremento
                for (int i = 3; i >= 0; i--) {
                    // I 3 byte meno significativi hanno limite massimo 255
                    if (i > 0) {
                        if (lastMulticastIP[i] < 255) {
                            lastMulticastIP[i]++;
                            break;
                        }
                    } else {
                        // Il byte più significativo invece 239
                        if (lastMulticastIP[i] < 239) lastMulticastIP[i]++;
                    }
                }
                // Riporto gli ottetti da int a String, in notazione decimale puntata
                multicastIP = Arrays.stream(lastMulticastIP)
                        .mapToObj(String::valueOf)
                        .collect(Collectors.joining("."));
            }
            // Aggiungo l'IP appena generato al file
            System.out.println(multicastIP);
            multicastJson.add("lastMulticastIP", JsonParser.parseString(multicastIP));
        }
        // Salvo tutto nuovamente su file, prima di ritornare l'IP
        try { Files.write(pathMulticast,multicastJson.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) { throw new MulticastException(); }

        if(DEBUG) System.out.println(multicastJson.toString());
        return multicastIP;
    }
    // * CALLBACKS
    @Override
    public synchronized void registerCallback(String username, NotifyEventInterface clientInterface)
            throws RemoteException {
        if(!clients.containsKey(username)) {
            clients.put(username,clientInterface);
            // Lista di stringhe che riporta lo stato di ogni utente del sistema
            List<String> usersStatus = new LinkedList<>();
            for(User user : users)
                // username:ONLINE oppure username:OFFLINE
                usersStatus.add(user.getUsername()+":"+user.getStatus().toString());
            clientInterface.notifyUsers(usersStatus);
        }
    }

    @Override
    public synchronized void unregisterCallback(String username, NotifyEventInterface clientInterface) throws RemoteException {
        clients.remove(username, clientInterface);
    }

    public synchronized void sendCallback() throws RemoteException{
        // Lista di stringhe che riporta lo stato di ogni utente del sistema
        List<String> usersStatus = new LinkedList<>();
        for(User user : users)
            // username:ONLINE oppure username:OFFLINE
            usersStatus.add(user.getUsername()+":"+user.getStatus().toString());

        if(DEBUG) System.out.println(usersStatus.toString());

        // Per ogni client registrato al servizio di callback,
        // invio la lista di utenti ed il loro stato
        for(Map.Entry<String, NotifyEventInterface> client : clients.entrySet())
            client.getValue().notifyUsers(usersStatus);

    }

    // * MAIN
    public static void main(String[] args){
        ServerMain server = new ServerMain();
        server.live();
    }
}
