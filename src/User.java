public class User {

    public enum Status { OFFLINE, ONLINE } // 0 = Offline, 1 = Online

    private final String username;
    private final String password;
    private Status status = Status.OFFLINE; // da ignorare nel json

    // TODO: set status on create
    // TODO: hash password

    public User(String username, String password){
        this.username = username;
        this.password = password;
    }

    public String getUsername() { return username; }

    public String getPassword() { return password; }

    public Status getStatus() { return status; }

    public void setStatus(Status status) { this.status = status; }
}
