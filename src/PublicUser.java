import java.io.Serializable;

public class PublicUser implements Serializable {

    private final String username;
    private User.Status status;

    public PublicUser(String username){
        this.username = username;
        this.status = User.Status.OFFLINE;
    }

    public PublicUser(String username, User.Status status){
        this.username = username;
        this.status = status;
    }

    public String getUsername() {
        return username;
    }

    public User.Status getStatus() {
        return status;
    }

    public void setStatus(User.Status status){ this.status = status; }
}