// @author Luca Cirillo (545480)

import WorthExceptions.UsernameAlreadyTakenException;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ServerRMI extends Remote {

    void register(String username, String password)
            throws RemoteException, UsernameAlreadyTakenException;

    void registerCallback(NotifyEventInterface clientInterface)
        throws RemoteException;

    void unregisterCallback(NotifyEventInterface clientInterface)
        throws RemoteException;
}
