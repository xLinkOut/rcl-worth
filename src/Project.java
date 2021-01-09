import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class Project {
    private final String name;
    private final List<User> members;

    // Cards // temp string substiture cards
    private final List<Card> todo;
    private final List<Card> inProgress;
    private final List<Card> toBeRevised;
    private final List<Card> done;


    public Project(String name){
        this.name = name;
        this.members = new ArrayList<>();

        // ArrayList or LinkedList ?
        this.todo = new LinkedList<>();
        this.inProgress = new LinkedList<>();
        this.toBeRevised = new LinkedList<>();
        this.done = new LinkedList<>();

    }

    public String getName() {
        return name;
    }

    public List<User> getMembers() {
        return members;
    }
}
