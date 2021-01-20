// @author Luca Cirillo (545480)

import java.rmi.Remote;
import java.rmi.RemoteException;

// Eccezioni
import WorthExceptions.UsernameAlreadyTakenException;

// Interfaccia per l'utilizzo client->server del meccanismo RMI
public interface ServerRMI extends Remote {

    // Registra un nuovo utente nel sistema
    void register(String username, String password)
            throws RemoteException, UsernameAlreadyTakenException;

    // Iscrive il client al servizio di ricezione notifiche dal server tramite callback
    void registerCallback(String username, NotifyEventInterface clientInterface)
        throws RemoteException;

    // Disiscrive il client dal servizio di ricezione notifiche dal server tramite callback
    void unregisterCallback(String username, NotifyEventInterface clientInterface)
        throws RemoteException;

}
