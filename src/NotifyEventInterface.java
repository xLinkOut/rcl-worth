import java.rmi.Remote;
import java.rmi.RemoteException;

public interface NotifyEventInterface extends Remote {

    void notifyEvent(int value)
            throws RemoteException;
}
