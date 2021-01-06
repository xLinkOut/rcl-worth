// @author Luca Cirillo (545480)

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
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
import java.util.Optional;

public class ServerMain extends RemoteObject implements Server, ServerRMI{

    // * TCP
    private static final int PORT_TCP = 6789;
    // * RMI
    private static final int PORT_RMI = 9876;
    private static final String NAME_RMI = "WORTH-SERVER";
    // * SERVER
    // TODO: boolean DEBUG
    //private static final Gson gson = new Gson();
    private final List<User> Users;
    private final List<PublicUser> PublicUsers;
    private final List<NotifyEventInterface> clients;

    public ServerMain(){
        // Callback
        super();
        clients = new ArrayList<>();

        // Persistenza
        this.Users = new ArrayList<>();
        this.PublicUsers = new ArrayList<>();
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

                // TODO: staccare client disconnesso
                // Se la key considerata identifica:
                try {

                    // Un canale pronto ad accettare una nuova connessione
                    if (key.isAcceptable()) {
                        ServerSocketChannel socket = (ServerSocketChannel) key.channel();
                        // Accetto la connessione
                        SocketChannel client = socket.accept();
                        System.out.println("<" + client.getRemoteAddress() + ">: TCP Accepted");
                        client.configureBlocking(false);
                        // Registro il nuovo client sul Selector
                        client.register(selector, SelectionKey.OP_READ, null);
                    }

                    // Un canale pronto ad un'operazione di lettura
                    else if (key.isReadable()) {
                        SocketChannel client = (SocketChannel) key.channel();
                        // Leggo il comando inviato dal client
                        ByteBuffer buffer = ByteBuffer.allocate(1024);
                        client.read(buffer);
                        // Lo trasformo in un array, con una split sullo spazio vuoto
                        // (Con la .trim() mi assicuro di eliminare spazi vuoti "inutili"
                        String[] cmd = new String(buffer.array()).trim().split(" ");
                        if (cmd.length > 0) {
                            switch (cmd[0]) {
                                case "login":
                                    if (login(cmd[1], cmd[2])) key.attach("ok");
                                    break;
                                case "logout":
                                    if(logout(cmd[1])) key.attach("ok");
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
                            key.cancel();
                            key.channel().close();
                            // TODO: Status offline
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
    public boolean register(String username, String password)
        throws RemoteException, IllegalArgumentException{
        // Controllo validità dei parametri
        if(username.isEmpty()) throw new IllegalArgumentException("username");
        if(password.isEmpty()) throw new IllegalArgumentException("password");

        // Controllo unicità username
        if(userExists(username)) return false;

        // Registro utente nel database
        Users.add(new User(username, password));
        // TODO: Valutare unmodifieble list
        PublicUsers.add(new PublicUser(username));
        //String jsonNewUser = gson.toJson(newUser);
        sendCallbacks();
        System.out.println("register("+username+","+password+")");
        return true;
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    private boolean login(String username, String password)
        throws IllegalArgumentException{
        // Controllo validità dei parametri
        if(username.isEmpty()) throw new IllegalArgumentException("username");
        if(password.isEmpty()) throw new IllegalArgumentException("password");

        // Controllo l'esistenza dell'utente
        if(!userExists(username)) return false;

        // Controllo se la password è corretta
        if(!getUser(username).get().getPassword().equals(password)) return false;

        // Aggiorno il suo stato su Online
        getUser(username).get().setStatus(User.Status.ONLINE);

        // Aggiorno tutti gli altri clients con una callback
        try { sendCallbacks(); }
        catch (RemoteException e) {e.printStackTrace();}

        return true;
    }

    private boolean logout(String username)
        throws IllegalArgumentException{
        // Controllo validità dei parametri
        if(username.isEmpty()) throw new IllegalArgumentException("username");

        // Controllo l'esistenza dell'utente
        // TODO Eccezione user not found?
        if(!userExists(username)) return false;

        // Limito l'overhead non facendo controlli
        // in quanto, se arriva un comando di logout
        // sicuramente l'utente ha fatto un login
        // precedentemente, per come il client è implementato
        // (Sono sicuro che utente esista)
        //noinspection OptionalGetWithoutIsPresent
        getUser(username).get().setStatus(User.Status.OFFLINE);

        // Aggiorno tutti gli altri clients con una callback
        try { sendCallbacks(); }
        catch (RemoteException e) {e.printStackTrace();}

        return true;
    }

    // * UTILS
    private boolean userExists(String username){
        return Users.stream().anyMatch(user -> user.getUsername().equals(username));
    }

    private Optional<User> getUser(String username){
        return Users.stream().filter(user -> user.getUsername().equals(username)).findFirst();
    }

    // * CALLBACKS
    @Override
    public synchronized void registerCallback(NotifyEventInterface clientInterface)
            throws RemoteException {
        if(!clients.contains(clientInterface)) {
            clients.add(clientInterface);
            System.out.println("New client registered for callbacks");
            synchronized (PublicUsers){
                clientInterface.notifyEvent(PublicUsers);
            }
        }
    }

    @Override
    public synchronized void unregisterCallback(NotifyEventInterface clientInterface) throws RemoteException {
        if(clients.remove(clientInterface)) System.out.println("Client unregistered");
        else System.out.println("Unable to unregister client");
    }

    public synchronized void sendCallbacks() throws RemoteException{
        //System.out.println("Starting callbacks...");
        for (NotifyEventInterface client : clients)
            client.notifyEvent(PublicUsers);
        //System.out.println("Callbacks complete");
    }

    // * MAIN
    public static void main(String[] args){
        ServerMain server = new ServerMain();
        server.live();
    }
}
