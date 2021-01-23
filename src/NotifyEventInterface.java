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
     */
    void notifyUsers(List<String> users)
            throws RemoteException;

    /* Permette di notificare un utente quando questo è stato aggiunto
       ad un progetto da un altro utente del sistema. Questa informazione
       viene utilizzata dal client per recuperare e salvare in locale
       le informazioni Multicast relative al progetto in questione,
       per poter utilizzare fin da subito la chat e non perdere dunque,
       nessun messaggio. Viene contestualmente inviato il nome dell'utente
       da cui è arrivato l'invito.
     */
    void notifyNewProject(String multicastInfo, String fromWho)
            throws RemoteException;

    /* Permette di notificare un utente quando un membro di un progetto di cui
       l'utente fa parte, richiede la cancellazione del progetto. Il client
       può così sincronizzare la cancellazione dei riferimenti locali del progetto,
       nonché interrompere il thread che altrimenti rimarrebbe in ascolto sulla chat.
       L'utente riceve inoltre una notifica testuale.
     */
    void notifyCancelProject(String projectName, String username)
        throws RemoteException;

    /* Funzione generica che permette al server di notificare il client
       quando si verifica un evento, inviando il relativo messaggio.
     */
    void notifyEvent(String message)
        throws RemoteException;

}
