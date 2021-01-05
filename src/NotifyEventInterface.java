import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface NotifyEventInterface extends Remote {

    void notifyEvent(List PublicUsers)
            throws RemoteException;
}
