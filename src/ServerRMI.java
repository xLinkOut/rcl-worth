// @author Luca Cirillo (545480)

import WorthExceptions.UsernameAlreadyTakenException;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ServerRMI extends Remote {

    void register(String username, String password)
            throws RemoteException, UsernameAlreadyTakenException;

    void registerCallback(String username, NotifyEventInterface clientInterface)
        throws RemoteException;

    void unregisterCallback(String username, NotifyEventInterface clientInterface)
        throws RemoteException;
}
