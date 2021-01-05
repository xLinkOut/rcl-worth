// @author Luca Cirillo (545480)

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ServerRMI extends Remote {

    boolean register(String username, String password)
            throws RemoteException;

    void registerCallback(NotifyEventInterface clientInterface)
        throws RemoteException;

    void unregisterCallback(NotifyEventInterface clientInterface)
        throws RemoteException;
}
