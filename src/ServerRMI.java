// @author Luca Cirillo (545480)

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ServerRMI extends Remote {

    // TODO: Deve essere public oppure può essere private? oppure niente?
    // TODO: Se restituissi un intero che definisce il tipo di errore?
    //  o meglio, definissi delle eccezioni per
    //      parametri non validi
    //      username già in uso
    boolean register(String username, String password)
            throws RemoteException;

}
