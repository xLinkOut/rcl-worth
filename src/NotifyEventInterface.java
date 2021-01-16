// @author Luca Cirillo (545480)
import java.util.List;
import java.rmi.Remote;
import java.rmi.RemoteException;

// Interfaccia di notifica server->client con meccanismo callback
public interface NotifyEventInterface extends Remote {

    void notifyEvent(List PublicUsers)
            throws RemoteException;

    /* Permette di notificare un utente quando questo è stato aggiunto
       ad un progetto da un altro utente. Viene utilizzata dal client per
       recuperare e salvare in locale le informazioni Multicast relative
       al progetto in questione, per poter utilizzare fin da subito la chat.
       Viene contestualmente inviato il nome dell'utente da cui è arrivato l'invito.
     */
    void notifyProject(String multicastInfo, String fromWho)
            throws RemoteException;
}
