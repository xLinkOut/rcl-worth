import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface NotifyEventInterface extends Remote {

    void notifyProject(String multicastInfo)
            throws RemoteException;

    void notifyEvent(List PublicUsers)
            throws RemoteException;
}
