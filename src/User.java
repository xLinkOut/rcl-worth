import java.util.ArrayList;
import java.util.List;

public class User {

    public enum Status { OFFLINE, ONLINE } // 0 = Offline, 1 = Online

    private final String username;
    private final String password;
    private Status status = Status.OFFLINE; // da ignorare nel json

    private final List<Project> projects;

    // TODO: hash password

    public User(String username, String password){
        this.username = username;
        this.password = password;
        this.projects = new ArrayList<>();
    }

    public String getUsername() { return username; }

    public String getPassword() { return password; }

    public Status getStatus() { return status; }

    public void setStatus(Status status) { this.status = status; }

    public boolean auth(String password){
        return this.password.equals(password);
    }

    public void addProject(Project project){
        this.projects.add(project);
    }
    public List<Project> getProjects(){
        return this.projects;
    }
    public void removeProject(Project project) {this.projects.remove(project);}
}
