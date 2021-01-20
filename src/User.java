// @author Luca Cirillo (545480)

// Jackson
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

// Utente del sistema WORTH
public class User {

    // Possibili stati dell'utente
    public enum Status { OFFLINE, ONLINE }

    private final String username; // Username dell'utente, univoco nel sistema
    private final String password; // Password dell'utente
    @JsonIgnore // Informazione che non deve persistere, sempre offline all'avvio del server
    private Status status = Status.OFFLINE; // Stato dell'utente


    @JsonCreator // Costruttore condiviso con Jackson
    public User(@JsonProperty("username") String username,
                @JsonProperty("password") String password){
        this.username = username;
        this.password = password;
    }

    // Getters
    public String getUsername() { return this.username; }
    public String getPassword() { return this.password; }
    public Status getStatus() { return this.status; }

    // Setters
    public void setStatus(Status status) { this.status = status; }

    // Authentication
    public boolean auth(String password){
        return this.password.equals(password);
    }

}
