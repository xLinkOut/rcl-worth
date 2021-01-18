// @author Luca Cirillo (545480)
import java.util.List;
import java.rmi.Remote;
import java.rmi.RemoteException;

// Interfaccia di notifica server->client con meccanismo callback
public interface NotifyEventInterface extends Remote {

    /* Permette di notificare un utente quando si verifica un cambio di stato
       per un qualunque altro utente del sistema. Il server notifica ogni client
       registrato al servizio di callback ogni qual volta cambia lo stato di uno
       degli utenti, sia esso da Offline ad Online (login) o viceversa (logout).
       Il client non interagisce in alcun modo con queste informazioni, che vengono
       inviate a titolo informativo, dunque una semplice lista di stringhe.
     */
    void notifyUsers(List<String> users)
            throws RemoteException;

    /* Permette di notificare un utente quando questo è stato aggiunto
       ad un progetto da un altro utente. Viene utilizzata dal client per
       recuperare e salvare in locale le informazioni Multicast relative
       al progetto in questione, per poter utilizzare fin da subito la chat.
       Viene contestualmente inviato il nome dell'utente da cui è arrivato l'invito.
     */
    void notifyProject(String multicastInfo, String fromWho)
            throws RemoteException;

    /* Funzione generica che permette al server di notificare il client
       quando si verifica un evento, inviando il relativo messaggio
     */
    void notifyEvent(String message)
        throws RemoteException;
}
