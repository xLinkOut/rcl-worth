// @author Luca Cirillo (545480)

import java.rmi.Remote;
import java.rmi.RemoteException;

import WorthExceptions.UsernameAlreadyTakenException;

// Interfaccia per l'utilizzo client->server del meccanismo RMI
public interface ServerRMI extends Remote {

    // Registra un nuovo utente nel sistema
    void register(String username, String password)
            throws RemoteException, UsernameAlreadyTakenException;

    // "Abbona" il client alla ricezione di eventi tramite callbacks
    void registerCallback(String username, NotifyEventInterface clientInterface)
        throws RemoteException;

    // "Disiscrive" il client dalla ricezione di eventi tramite callback
    void unregisterCallback(String username, NotifyEventInterface clientInterface)
        throws RemoteException;
}
