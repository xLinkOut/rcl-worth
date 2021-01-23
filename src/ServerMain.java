// @author Luca Cirillo (545480)

import java.net.*;
import java.util.*;
import java.io.File;
import java.nio.file.Path;
import java.io.IOException;
import java.nio.file.Paths;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.Files;
import java.rmi.RemoteException;
import java.rmi.registry.Registry;
import java.util.stream.Collectors;
import java.rmi.server.RemoteObject;
import java.rmi.AlreadyBoundException;
import java.rmi.registry.LocateRegistry;
import java.nio.charset.StandardCharsets;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.ConcurrentHashMap;

// Jackson
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.InjectableValues;
import com.fasterxml.jackson.databind.SerializationFeature;

// Eccezioni
import WorthExceptions.*;

// TODO: liste principali nuovamente final?
// TODO: mappare gli user con un dizionario?
// TODO: traduzione di messaggi in inglese
// TODO: togliere la sleep
// TODO: computeIfAbsent
// TODO: eliminare i residui di dipendenza circolare user<->project
// TODO: ricontrollare tutte le .printStackTrace()
// TODO: argomento da relazione: Ignoro tutti i comandi in eccedenza
// TODO: implementare la thread pool per i chat listeners
// TODO: chiudere tutte le connessioni allo shutdown
// TODO: leggere tutto il messaggio che arriva dal client con un buffer e do/while

@SuppressWarnings({"AccessStaticViaInstance", "ResultOfMethodCallIgnored"})
// Server WORTH
public class ServerMain extends RemoteObject implements ServerRMI{
    // * TCP
    private static final int PORT_TCP = 6789;
    // * RMI
    private static final int PORT_RMI = 9876;
    private static final String NAME_RMI = "WORTH-SERVER";
    // * PATHS
    private static final Path pathData = Paths.get("data/");
    private static final Path pathUsers = Paths.get("data/Users.json");
    private static final Path pathProjects = Paths.get("data/Projects/");
    private static final Path pathMulticast = Paths.get("data/Multicast.json");
    // * SERVER
    private static final boolean DEBUG = true;
    private static final Random random = new Random();
    private static final ObjectMapper jacksonMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT); // Attiva il Pretty-Print
    private static Multicast multicast;

    // Restore <final> keyword
    private static List<User> users;
    private static Map<String, NotifyEventInterface> clients;
    private static Map<String, Project> projects;

    // Inizializza il sistema oppure ripristina l'ultima sessione
    public ServerMain(){
        super(); // Callback

        // Persistenza
        try{
            // Se la directory "data/" non esiste,
            // probabilmente è il primo avvio del sistema
            if(Files.notExists(pathData)){
                if (DEBUG) System.out.println("System bootstrap: initialization of files and system structures");
                // Costruisco l'albero di directories e creo i files config di default
                Files.createDirectory(pathData);     // data/
                Files.createDirectory(pathProjects); // data/Projects/
                Files.createFile(pathUsers);         // data/Users.json
                Files.createFile(pathMulticast);     // data/Multicast.json
                // Inizializzo l'oggetto Multicast
                this.multicast = new Multicast();
                // E ne salvo il contenuto di default su disco
                jacksonMapper.writeValue(Files.newBufferedWriter(pathMulticast),this.multicast);
                // Inizializzo le strutture dati locali
                this.users = new ArrayList<>();
                this.projects = new ConcurrentHashMap<>();
            }else {
                // La directory "data/" esiste, probabilmente
                // il sistema è già stato avviato prima d'ora
                // Cerco eventuali sessioni precedenti da ripristinare

                // Se la directory "data/Projects/" esiste
                if (Files.exists(pathProjects)) {
                    // Creo la struttura dati che ospiterà i progetti
                    this.projects = new ConcurrentHashMap<>();
                    // Elenco i progetti presenti in "data/Projects/"
                    List<Path> subfolders = Files.walk(pathProjects, 1)
                            .filter(Files::isDirectory)
                            .collect(Collectors.toList());
                    // Rimuovo il primo elemento, che corrisponde proprio
                    // alla cartella "data/Projects/"
                    subfolders.remove(0);

                    // Per ogni progetto presente, ne ricostruisco la struttura
                    for (Path projectFolder : subfolders) {
                        // Mantengo il path diretto al file config del progetto
                        Path projectConfigFile = Paths.get(
                                projectFolder.toString()+"/"+projectFolder.getFileName()+".json");

                        // Se il file di progetto "<projectName>.json" non esiste,
                        // il progetto è corrotto e non posso ricostruirlo;
                        // provvedo quindi ad eliminarlo dal disco
                        if (Files.notExists(projectConfigFile)) {
                            try {Files.walk(projectFolder)
                                    .sorted(Comparator.reverseOrder())
                                    .map(Path::toFile)
                                    .forEach(File::delete);
                                Files.delete(projectFolder);
                            } catch (IOException ignored) {
                                System.err.println("Progetto corrotto cancellare manualmente");
                            }
                        }

                        // Elenco tutti i files nella cartella, relativi alle cards
                        List<Path> projectFiles = Files.walk(projectFolder, 1)
                                .filter(Files::isRegularFile)
                                .collect(Collectors.toList());
                        // Rimuovo dalla lista il path del config file
                        projectFiles.remove(projectConfigFile);

                        // Carico in memoria le cards
                        List<Card> cards = new ArrayList<>();
                        for (Path cardFile : projectFiles)
                            cards.add(jacksonMapper.readValue(Files.newBufferedReader(cardFile), Card.class));

                        // Possibilità di Jackson di "iniettare" oggetti esterni durante
                        // la deserializzazione di un file JSON
                        jacksonMapper.setInjectableValues(new InjectableValues.Std().addValue("cards", cards));
                        // Carico in memoria il progetto
                        projects.put(
                                projectFolder.getFileName().toString(), // Nome del progetto
                                jacksonMapper.readValue(Files.newBufferedReader(projectConfigFile), Project.class)
                        );
                    }

                    if (DEBUG) System.out.println("Loaded " + this.projects.size() + " projects");

                } else {
                    // Altrimenti creo la cartella per contenere i futuri progetti
                    Files.createDirectory(pathProjects);
                    // Ed inizializzo la struttura dati senza caricare nulla
                    this.projects = new ConcurrentHashMap<>();
                    if (DEBUG) System.out.println("No projects found");
                }

                // Se "data/Users.json" esiste e non è vuoto, carico in memoria la lista di utenti registrati
                if (Files.exists(pathUsers) && Files.size(pathUsers) > 0) {
                    this.users = jacksonMapper.readValue(
                            Files.newBufferedReader(pathUsers),
                            new TypeReference<>() {});
                    if (DEBUG) System.out.println("Loaded " + this.users.size() + " users");

                // Altrimenti inizializzo la struttura dati vuota
                } else {
                    this.users = new ArrayList<>();
                    if (DEBUG) System.out.println("No users found");
                }

                // Se il "data/Multicast.json" esiste e non è vuoto, ne carico il contenuto
                if(Files.exists(pathMulticast) && Files.size(pathMulticast) > 0){
                    this.multicast = jacksonMapper.readValue(Files.newBufferedReader(pathMulticast),Multicast.class);
                    if (DEBUG) {
                        System.out.println("Last used multicast IP: "+this.multicast.getLastIP());
                        System.out.println("There are "+this.multicast.getReleasedIP().size()+" free IP address");
                    }
                }else{
                    // Altrimenti ne creo uno di default
                    this.multicast = new Multicast();
                    // E lo salvo su disco
                    jacksonMapper.writeValue(Files.newBufferedWriter(pathMulticast), this.multicast);
                }
            }

            // Inizializzo la struttura dati utilizzata per le callbacks,
            // che viene riempita solamente a runtime e non persiste sul disco
            this.clients = new LinkedHashMap<>();

        } catch (IOException ioe) {
            System.err.println("An error occurred during system initialization, closing.");
            if (DEBUG) ioe.printStackTrace();
            System.exit(-1);
        }
    }

    // Server go live!
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
                Thread.sleep(300); // Limita overhead
                selector.select();
            } catch (IOException ioe) { if (DEBUG) ioe.printStackTrace(); break;
            } catch (InterruptedException ie) { if (DEBUG) ie.printStackTrace(); }

            // Iteratore sui canali che risultano pronti
            Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();

            while (iterator.hasNext()) {
                // Prendo la prossima key
                SelectionKey key = iterator.next();
                // La rimuovo esplicitamente
                iterator.remove();

                // Se la key considerata identifica:
                try {

                    // Un canale pronto ad accettare una nuova connessione
                    // (Client connesso)
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
                                        key.attach("ok:Now your are logged as "+cmd[1]+"!:"+login(cmd[1], cmd[2]));
                                    } catch (AuthenticationFailException afe) {
                                        key.attach("ko:401:The password you entered is incorrect, please try again");
                                    } catch (UserNotFoundException unfe) {
                                        key.attach("ko:404:Are you sure that an account with this name exists?\nIf you need one, use register command");
                                    } catch (AlreadyLoggedException ale) {
                                        key.attach("ko:409:A session has already been started with this account, use that or logout first");
                                    }
                                    break;

                                case "logout":
                                    try{
                                        // cmd[1] = username, nome utente del proprio account
                                        logout(cmd[1]);
                                        key.attach("ok:You have been logged out successfully");
                                    } catch (UserNotFoundException unfe) {
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
                                    } catch (NameAlreadyInUse pnaiue) {
                                        key.attach("ko:409:The name chosen for the project is already in use, try another one!");
                                    } catch (NoMulticastAddressAvailableException nmaae) {
                                        key.attach("ko:507:There are no more IP addresses available for creating a new project! Please try again later, new ones may be released");
                                    }
                                    break;

                                case "addMember":
                                    try{
                                        // cmd[1] = username, nome utente del proprio account
                                        // cmd[2] = projectName, nome del progetto
                                        // cmd[3] = memberUsername, username dell'utente da inserire
                                        addMember(cmd[1],cmd[2],cmd[3]);
                                        key.attach("ok:"+cmd[3]+" added to "+cmd[2]+"!");
                                    } catch (ForbiddenException fe) {
                                        key.attach("ko:403:You're not member of this project");
                                    } catch (ProjectNotFoundException pnfe) {
                                        key.attach("ko:404:Can't found "+cmd[2]+", are you sure that exists? Try createProject to create a project");
                                    } catch (UserNotFoundException unfe) {
                                        key.attach("ko:404:Can't found an account named "+cmd[3]+"! Maybe is a typo?");
                                    } catch (AlreadyMemberException ame) {
                                        key.attach("ko:409:" + cmd[3] + " is already a member of " + cmd[2]);
                                    } catch (RemoteException re){
                                        key.attach("ko:500:Project created, but failed to update local chat information. Please login and logout again to properly use project chat!");
                                    }
                                    break;

                                case "showMembers":
                                    try{
                                        // cmd[1] = username, nome utente del proprio account
                                        // cmd[2] = projectName, nome del progetto
                                        key.attach("ok:"+showMembers(cmd[1], cmd[2]));
                                    } catch (ForbiddenException fe) {
                                        key.attach("ko:403:You're not member of this project");
                                    } catch (ProjectNotFoundException pnfe) {
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
                                    } catch (ForbiddenException fe) {
                                        key.attach("ko:403:You're not member of this project");
                                    } catch (ProjectNotFoundException pnfe) {
                                        key.attach("ko:404:Can't found "+cmd[2]+", are you sure that exists? Try createProject to create a project");
                                    } catch (CardAlreadyExists cae) {
                                        key.attach("ko:409:A card with the same name already exists");
                                    }
                                    break;

                                case "showCard":
                                    try{
                                        // cmd[1] = username, nome utente del proprio account
                                        // cmd[2] = projectName, nome del progetto
                                        // cmd[3] = cardName, nome della card
                                        key.attach("ok:"+showCard(cmd[1],cmd[2],cmd[3]));
                                    } catch (ForbiddenException fe) {
                                        key.attach("ko:403:You're not member of this project");
                                    } catch (ProjectNotFoundException pnfe) {
                                        key.attach("ko:404:Can't found "+cmd[2]+", are you sure that exists? Try createProject to create a project");
                                    } catch (CardNotFoundException cnfe) {
                                        key.attach("ko:404:Can't found "+cmd[3]+", are you sure that exists? Try addCard to create a card");
                                    }
                                    break;

                                case "showCards":
                                    try{
                                        // cmd[1] = username, nome utente del proprio account
                                        // cmd[2] = projectName, nome del progetto
                                        key.attach("ok:"+showCards(cmd[1],cmd[2]));
                                    } catch (ForbiddenException fe) {
                                        key.attach("ko:403:You're not member of this project");
                                    } catch (ProjectNotFoundException pnfe) {
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
                                    } catch (ForbiddenException fe) {
                                        key.attach("ko:403:You're not member of this project");
                                    } catch (ProjectNotFoundException pnfe) {
                                        key.attach("ko:404:Can't found "+cmd[2]+", are you sure that exists? Try createProject to create a project");
                                    } catch (CardNotFoundException cnfe) {
                                        key.attach("ko:404:Can't found "+cmd[3]+", are you sure that exists? Try addCard to create a card");
                                    } catch (IllegalCardMovementException icme) {
                                        key.attach("ko:406:You can't move a card from "+cmd[4]+" to "+cmd[5]);
                                    }
                                    break;

                                case "getCardHistory":
                                    try{
                                        // cmd[1] = username, nome utente del proprio account
                                        // cmd[2] = projectName, nome del progetto
                                        // cmd[3] = cardName, nome della card
                                        // cmd[3] = cardName, nome della card
                                        key.attach("ok:"+getCardHistory(cmd[1],cmd[2],cmd[3]));
                                    } catch (ForbiddenException fe) {
                                        key.attach("ko:403:You're not member of this project");
                                    } catch (ProjectNotFoundException pnfe) {
                                        key.attach("ko:404:Can't found "+cmd[2]+", are you sure that exists? Try createProject to create a project");
                                    } catch (CardNotFoundException cnfe) {
                                        key.attach("ko:404:Can't found "+cmd[3]+", are you sure that exists? Try addCard to create a card");
                                    }
                                    break;

                                case "listProjects":
                                    try{
                                        // cmd[1] = username, nome utente del proprio account
                                        key.attach("ok:"+listProjects(cmd[1]));
                                    } catch (ProjectNotFoundException pnfe) {
                                        key.attach("ko:404:You are not a member of any project, yet");
                                    }
                                    break;

                                case "cancelProject":
                                    try{
                                        cancelProject(cmd[1],cmd[2]);
                                        key.attach("ok:Project "+cmd[2]+" was canceled");
                                    } catch (ForbiddenException fe) {
                                        key.attach("ko:403:You're not member of this project");
                                    } catch (ProjectNotFoundException pnfe) {
                                        key.attach("ko:404:Can't found "+cmd[2]+", are you sure that exists? Try createProject to create a project");
                                    } catch (PendingCardsException pce) {
                                        key.attach("ko:412:Not all cards are in the done list. There is still work to be done!");
                                    }

                                // Disconnessione "hard" del client (processo interrotto)
                                case "":
                                    // Imposto lo stato dell'utente disconnesso su OFFLINE
                                    pingPong();
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
                } catch (IOException ioe) { ioe.printStackTrace(); }
            }
        }
    }

    // * ENDPOINTS

    // Registra un nuovo utente nel sistema
    public synchronized void register(String username, String password)
            throws RemoteException, IllegalArgumentException, UsernameAlreadyTakenException {

        // Controllo validità dei parametri
        if(username.isEmpty()) throw new IllegalArgumentException("username");
        if(password.isEmpty()) throw new IllegalArgumentException("password");
        if(DEBUG) System.out.println("Server@WORTH > register "+username+" "+password);

        // Controllo unicità username
        if(userExists(username)) throw new UsernameAlreadyTakenException("username");

        // Aggiungo il nuovo utente al sistema
        users.add(new User(username, password));
        // E ne tengo traccia anche sul filesystem
        try {
            jacksonMapper.writeValue(Files.newBufferedWriter(pathUsers), users);
        } catch (IOException ioe) {
            if (DEBUG) ioe.printStackTrace();
            System.err.println("I was unable to save user information on the filesystem, on next restart they will probably be lost ...");
        }

        // Aggiorno tutti gli altri clients con una callback
        // per informarli che un nuovo si è registrato, e risulta quindi offline
        try { sendCallback(); }
        catch (RemoteException re) { re.printStackTrace(); }
    }

    // Permette ad un utente di utilizzare il sistema, segnando il suo stato come ONLINE
    private String login(String username, String password)
            throws UserNotFoundException, AuthenticationFailException, AlreadyLoggedException {
        if(DEBUG) System.out.println("Server@WORTH > login "+username+" "+password);

        // Controllo l'esistenza dell'utente
        if(!userExists(username)) throw new UserNotFoundException(username);

        // Recupero le informazioni dell'utente
        User user = getUser(username);
        
        // Controllo se la password è corretta
        if(!user.authentication(password)) throw new AuthenticationFailException(password);

        // Controllo che l'utente non sia già connesso
        // (non sono ammessi login multipli)
        if(user.getStatus() == User.Status.ONLINE) throw new AlreadyLoggedException();

        // Aggiorno il suo stato su Online
        user.setStatus(User.Status.ONLINE);
        //getPublicUser(username).setStatus(User.Status.ONLINE);

        // Aggiorno tutti gli altri clients con una callback
        // per informarli che l'utente è online
        try { sendCallback(); }
        catch (RemoteException re) { re.printStackTrace(); }

        // Ritorno le informazioni Multicast su tutti i
        // (eventuali) progetti di cui l'utente è membro
        // TODO: devo aver già caricato i progetti in memoria, ovviamente
        List<Project> userProjects = getUserProjects(username);
        // Se l'utente non è membro di nessun progetto, ritorno una stringa vuota
        if(userProjects.isEmpty()) return "";
        StringBuilder output = new StringBuilder();
        for(Project project : userProjects) output.append(project.getMulticastInfo());
        return output.toString();

    }

    // Un utente abbandona il sistema, ed il suo stato passa su OFFLINE
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
        catch (RemoteException re) {re.printStackTrace();}
    }

    // Restituisce la lista dei progetto di cui l'utente ne è membro
    private String listProjects(String username)
            throws ProjectNotFoundException {
        if(DEBUG) System.out.println("Server@WORTH > listProjects "+username);

        List<Project> list = getUserProjects(username);
        if(list.size() == 0) throw new ProjectNotFoundException(username);
        return list.stream()
                .map(Project::getName)
                .collect(Collectors.toList()).toString();
    }

    // Un utente richiede la creazione di un nuovo progetto
    private String createProject(String username, String projectName)
            throws NameAlreadyInUse, NoMulticastAddressAvailableException {

        if(DEBUG) System.out.println("Server@WORTH > createProject "+username+" "+projectName);

        // Controllare che non esista già un progetto con lo stesso nome
        if(projects.containsKey(projectName)) throw new NameAlreadyInUse(projectName);

        // Creo un nuovo progetto e lo aggiungo al database
        //User user = getUser(username);
        Project project = new Project(projectName, username, genMulticastIP(),1025+random.nextInt(64510));
        projects.put(projectName, project);
        //user.addProject(project);
        // TODO: eliminare se si riesce a togliere la dipendenza da user.projects

        // Scrivo su disco tutti i dettagli del progetto
        try {
            Path projectPath = Paths.get(pathProjects.toString()+"/"+projectName);
            Path projectConfigPath = Paths.get(projectPath.toString()+"/"+projectName+".json");
            Files.createDirectory(projectPath);
            Files.createFile(projectConfigPath);
            jacksonMapper.writeValue(Files.newBufferedWriter(projectConfigPath),project);
        } catch (IOException e) {
            // TODO: notificare l'utente dell'errore, probabilmente il nome collide con i criteri del filesystem
            System.out.println("An error occurred trying to save the project "+projectName+" to the file system!");
        }

        try {
            jacksonMapper.writeValue(Files.newBufferedWriter(pathUsers), users);
        } catch (IOException ioe) {
            if (DEBUG) ioe.printStackTrace();
            System.err.println("I was unable to save user information on the filesystem, on next restart they will probably be lost ...");
        }

        // Ritorno le informazioni multicast da passare all'utente
        // affinché possa utilizzare la chat di progetto
        return project.getMulticastInfo();
    }

    // Un utente membro di un progetto richiede di aggiungere un nuovo membro
    private void addMember(String username, String projectName, String memberUsername)
            throws ProjectNotFoundException, UserNotFoundException, AlreadyMemberException, ForbiddenException, RemoteException {

        if(DEBUG) System.out.println("Server@WORTH > addMember "+username+" "+projectName+" "+memberUsername);

        // Controllo se esiste un progetto con il nome indicato
        if(!projects.containsKey(projectName)) throw new ProjectNotFoundException(projectName);
        // Controllo che memberUsername sia effettivamente un utente del sistema
        if(!userExists(memberUsername)) throw new UserNotFoundException(memberUsername);

        Project project = projects.get(projectName);
        //User user = getUser(memberUsername);

        // controllo che username sia un membro di projectName
        if(!project.getMembers().contains(username)) throw new ForbiddenException();

        // Controllo che memberUsername non faccia già parte di projectName
        if(project.getMembers().contains(memberUsername))
            throw new AlreadyMemberException(memberUsername);

        // Aggiungo memberUsername come nuovo membro del progetto projectName
        project.addMember(memberUsername);
        //user.addProject(project);
        // TODO: eliminare se si riesce a togliere la dipendenza da user.projects
        //try {
        //    jacksonMapper.writeValue(Files.newBufferedWriter(pathUsers), users);
        //} catch (IOException ioe) {
        //    if (DEBUG) ioe.printStackTrace();
        //    System.err.println("I was unable to save user information on the filesystem, on next restart they will probably be lost ...");
        //}
        // Salvo i dati aggiornati del progetto su disco
        try{
            jacksonMapper.writeValue(
                    Files.newBufferedWriter(Paths.get(pathProjects.toString()+"/"+projectName+"/"+projectName+".json")),
                    project);
        } catch (IOException e) {
            e.printStackTrace();
        }
        // Invio un avviso all'utente aggiunto, passandogli le informazioni necessarie ad
        // utilizzare la chat ed informandolo su chi lo ha aggiunto al progetto
        clients.get(memberUsername).notifyProject(project.getMulticastInfo(), username);
    }

    // Restituisce la lista dei membri che fanno parte di un progetto
    private String showMembers(String username, String projectName)
            throws ProjectNotFoundException, ForbiddenException {

        if(DEBUG) System.out.println("Server@WORTH > showMembers "+username+" "+projectName);

        // Controllo se esiste un progetto con il nome indicato
        if(!projects.containsKey(projectName)) throw new ProjectNotFoundException(projectName);

        Project project = projects.get(projectName);
        // Se l'utente è un membro del progetto, può consultare la lista membri
        if(project.getMembers().contains(username))
            return project.getMembers().toString();
        else throw new ForbiddenException();
    }

    // Un membro di un progetto aggiunge ad esso una nuova card
    private void addCard(String username, String projectName, String cardName, String cardDescription)
            throws ProjectNotFoundException, ForbiddenException, CardAlreadyExists {

        if (DEBUG)
            System.out.println("Server@WORTH > addCard " + username + " " + projectName + " " + cardName + " " + cardDescription);

        // Controllare che il progetto esista
        if (!projects.containsKey(projectName)) throw new ProjectNotFoundException(projectName);
        Project project = projects.get(projectName);

        // Controllare che l'utente sia un membro del progetto
        if (!project.getMembers().contains(username)) throw new ForbiddenException();
        // Controllare che non ci sia già una card con lo stesso nome
        if (project.getCard(cardName) != null) throw new CardAlreadyExists(cardName);
        // Creare la nuova card
        Card card = project.addCard(cardName, cardDescription);
        // Salvo le informazioni su disco
        try {
            jacksonMapper.writeValue(Files.newBufferedWriter(
                    Paths.get(pathProjects.toString() + "/" + projectName + "/" + cardName + ".json")), card);
        // TODO avviso
        } catch (IOException e) { e.printStackTrace(); }
    }

    // Restituisce le informazioni su una card di un progetto
    private String showCard(String username, String projectName, String cardName)
            throws CardNotFoundException, ForbiddenException, ProjectNotFoundException {
        if(DEBUG) System.out.println("Server@WORTH > showCard "+username+" "+projectName+" "+cardName);

        // Controllare che il progetto esista
        if(!projects.containsKey(projectName)) throw new ProjectNotFoundException(projectName);
        Project project = projects.get(projectName);

        // Controllare che l'utente sia un membro del progetto
        if(!project.getMembers().contains(username)) throw new ForbiddenException();

        Card card = project.getCard(cardName);
        if(card == null) throw new CardNotFoundException(cardName);
        return card.toString();
    }

    // Ritorna la lista delle cards presenti in un progetto
    private String showCards(String username, String projectName)
            throws ProjectNotFoundException, ForbiddenException {
        if(DEBUG) System.out.println("Server@WORTH > showCards "+username+" "+projectName);

        // Controllare che il progetto esista
        if(!projects.containsKey(projectName)) throw new ProjectNotFoundException(projectName);
        Project project = projects.get(projectName);

        // Controllare che l'utente sia un membro del progetto
        if(!project.getMembers().contains(username)) throw new ForbiddenException();

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

    // Sposta una card dalla sezione in cui si trova attualmente ad un'altra
    private void moveCard(String username, String projectName, String cardName, String from, String to)
            throws ProjectNotFoundException, ForbiddenException, CardNotFoundException, IllegalCardMovementException {
        // Controllare che il progetto esista
        if(!projects.containsKey(projectName)) throw new ProjectNotFoundException(projectName);
        Project project = projects.get(projectName);

        // Controllare che l'utente sia un membro del progetto
        if(!project.getMembers().contains(username)) throw new ForbiddenException();

        // Controllo che esista la card
        if(project.getCard(cardName) == null) throw new CardNotFoundException(cardName);

        // Controllo se from e to sono liste valide
        // TODO: try/catch ? what if different string
        Project.Section fromSection = Project.Section.valueOf(from.toUpperCase());
        Project.Section toSection   = Project.Section.valueOf(to.toUpperCase());

        // Sposto la card (il rispetto dei vincoli è assicurato dal metodo invocato
        Card card = project.moveCard(cardName,fromSection,toSection);

        // Salvo le modifiche della card su disco
        try {
            jacksonMapper.writeValue(Files.newBufferedWriter(
                    Paths.get(pathProjects.toString() + "/" + projectName + "/" + cardName + ".json")), card);
            // TODO avviso
        } catch (IOException e) { e.printStackTrace(); }

        // Invio una notifica sulla chat di progetto
        String message = getTime()+" WORTH: "+username+" has moved card "+cardName+
                " from "+fromSection.toString() +" to "+toSection.toString();
        try {
            new DatagramSocket().send(new DatagramPacket(
                    message.getBytes(StandardCharsets.UTF_8), message.length(),
                    InetAddress.getByName(project.getMulticastIP()), project.getMulticastPort()));
        } catch (UnknownHostException uhe) {
            System.out.println("I'm not sure if I have the correct address for the project "+projectName+", I can't notify all other members for now, sorry.");
        } catch (IOException ioe) {
            System.out.println("There was an error trying to notify all other members.");
        }

    }

    // Restituisce la history di una card
    private String getCardHistory(String username, String projectName, String cardName)
            throws CardNotFoundException, ForbiddenException, ProjectNotFoundException {
        // Controllare che il progetto esista
        if(!projects.containsKey(projectName)) throw new ProjectNotFoundException(projectName);
        Project project = projects.get(projectName);

        // Controllare che l'utente sia un membro del progetto
        if(!project.getMembers().contains(username)) throw new ForbiddenException();

        // Controllo che esista la card
        if(project.getCard(cardName) == null) throw new CardNotFoundException(cardName);

        // Ritorno la history della card
        return project.getCard(cardName).getHistory();
    }

    // Cancella il progetto se tutti i tasks sono stati svolti
    private void cancelProject(String username, String projectName)
            throws ProjectNotFoundException, ForbiddenException, PendingCardsException {
        // Controllare che il progetto esista
        if(!projects.containsKey(projectName)) throw new ProjectNotFoundException(projectName);
        Project project = projects.get(projectName);

        // Controllare che l'utente sia un membro del progetto
        if(!project.getMembers().contains(username)) throw new ForbiddenException();

        // Controllo che tutte le cards siano nella lista DONE
        if(project.canDelete()){
            // Salvo l'IP del progetto per essere riutilizzato
            try{ saveReleasedIP(project.getMulticastIP());
            } catch (MulticastException ignored) {}
            // Rimuovo il progetto dalle liste personali di tutti i membri
            // (compreso l'utente che ha richiesto la cancellazione)
            // e notifica subito l'utente dell'evento
            for(String user : project.getMembers()) {
                // TODO: non inviare la notifica alla stessa persona che ha cancellato il progetto
                try { clients.get(user).notifyEvent(
                        "Ding! "+username+" ha cancellato il progetto "+projectName);
                } catch (RemoteException re) {re.printStackTrace();}
            }
            // Rimuovo il progetto dalla lista globale
            projects.remove(projectName);
            // Rimuovo tutti i riferimenti sul filesystem
            // https://stackoverflow.com/questions/35988192/java-nio-most-concise-recursive-directory-delete
            try {
                Path projectPath = Paths.get(pathProjects.toString()+"/"+projectName);
                Files.walk(projectPath)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        //.peek(System.out::println)
                        .forEach(File::delete);
            } catch (IOException e) {
                // TODO: avviso
                e.printStackTrace();
            }
        }else throw new PendingCardsException();
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

        //if(DEBUG) System.out.println("Server@WORTH > "+clientRequest);
        return clientRequest.toString();
    }

    // Controlla l'esistenza di un utente nel sistema
    private boolean userExists(String username){
        return users.stream().anyMatch(user -> user.getUsername().equals(username));
    }

    // Ritorna il riferimento ad uno specifico utente
    private User getUser(String username){
        for(User user: users)
            if(user.getUsername().equals(username)) return user;
        return null;
    }

    // Restituisce l'ora corrente in formato (HH:MM)
    private String getTime(){
        Calendar now = Calendar.getInstance();
        return String.format("(%02d:%02d)",
                now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE));
    }

    // Genera un indirizzo IP multicast, sia esso nuovo o riutilizzato
    private String genMulticastIP() throws NoMulticastAddressAvailableException {
        // Genero un nuovo indirizzo IP multicast
        String multicastIP = multicast.getNewIP();
        // Salvo tutto su file, prima di ritornare l'IP
        try { jacksonMapper.writeValue(Files.newBufferedWriter(pathMulticast),multicast);
        } catch (IOException e) { System.err.println("I was unable to save multicast information on the filesystem, on next restart they will probably be lost ..."); }

        if(DEBUG) System.out.println(multicastIP);
        return multicastIP;
    }

    // Aggiunge l'indirizzo IP multicast alla lista degli indirizzi rilasciati
    private void saveReleasedIP(String IP) throws MulticastException {
        // Aggiungo l'indirizzo IP rilasciato alla lista
        multicast.addReleasedIP(IP);
        // Salvo tutto su file
        try { jacksonMapper.writeValue(Files.newBufferedWriter(pathMulticast),multicast);
        } catch (IOException e) { System.err.println("I was unable to save multicast information on the filesystem, on next restart they will probably be lost ..."); }
    }

    private List<Project> getUserProjects(String username){
        return projects.values().stream()
                .filter(project -> project.getMembers().contains(username))
                .collect(Collectors.toList());
    }

    // * CALLBACKS

    @Override
    // Iscrive il client alla ricezione di eventi tramite callback
    public synchronized void registerCallback(String username, NotifyEventInterface clientInterface)
            throws RemoteException {
        if(!clients.containsKey(username)) {
            clients.put(username,clientInterface);
            // Lista di stringhe che riporta lo stato di ogni utente del sistema
            List<String> usersStatus = new LinkedList<>();
            for(User user : users)
                // username:ONLINE oppure username:OFFLINE
                usersStatus.add(user.getStatus().toString()+":"+user.getUsername());
            clientInterface.notifyUsers(usersStatus);
        }
    }

    @Override
    // Disiscrive il client dalla ricezione di eventi tramite callback
    public synchronized void unregisterCallback(String username, NotifyEventInterface clientInterface)
            throws RemoteException {
        clients.remove(username, clientInterface);
    }

    // Invia a tutti i clients registrati al servizio di callback
    // informazioni sugli utenti e sul loro stato
    public synchronized void sendCallback() throws RemoteException{
        // Lista di stringhe che riporta lo stato di ogni utente del sistema
        List<String> usersStatus = new LinkedList<>();
        for(User user : users)
            // ONLINE:username oppure OFFLINE:username
            // Mettere prima lo stato invece l'utente permette, lato client,
            // di evitare l'utilizzo del metodo .split(":") ma agire invece
            // attraverso il metodo .substring(), e non allocare inutilmente
            // in memoria arrays (anche se di piccole dimensioni) per ogni utente.
            // Si mantiene l'utilizzo dei due punti per maggiore leggibilità in fase di debug
            usersStatus.add(user.getStatus().toString()+":"+user.getUsername());

        if(DEBUG) System.out.println(usersStatus.toString());

        // Per ogni client registrato al servizio di callback,
        // invio la lista di utenti ed il loro stato
        for(Map.Entry<String, NotifyEventInterface> client : clients.entrySet())
            client.getValue().notifyUsers(usersStatus);
    }

    // Se si verifica una disconnessione forzata di un client
    // "sondo" la rete per impostare lo stato del relativo utente su OFFLINE
    // Quindi invio le callback per aggiornare i clients sullo stato degli utenti
    private void pingPong() {
        for(Map.Entry<String, NotifyEventInterface> client : clients.entrySet()) {
            try {
                client.getValue().notifyEvent("");
            } catch (RemoteException re) {
                // Imposto lo stato dell'utente su OFFLINE
                getUser(client.getKey()).setStatus(User.Status.OFFLINE);
                // Rimuovo l'interfaccia per le callback
                clients.remove(client.getKey(), client.getValue());
                // Ho trovato il client disconnesso, mi fermo
                break;
            }
        }

        // Aggiorno tutti gli altri clients
        try { sendCallback();
        } catch (RemoteException re) { if (DEBUG) re.printStackTrace(); }
    }

    // * MAIN
    public static void main(String[] args){
        ServerMain server = new ServerMain();
        server.live();
    }
}
