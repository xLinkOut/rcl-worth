import java.io.Serializable;

public class PublicUser implements Serializable {

    private final String username;
    private final User.Status status;

    public PublicUser(String username, User.Status status){
        this.username = username;
        this.status = status;
    }

    public PublicUser(String username){
        this.username = username;
        this.status = User.Status.OFFLINE;
    }

    public String getUsername() {
        return username;
    }

    public User.Status getStatus() {
        return status;
    }
}