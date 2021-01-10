import java.util.ArrayList;
import java.util.List;

public class Project {
    // controlli su tutte le funzioni per vedere se user in members
    public enum Section { TODO, INPROGRESS, TOBEREVISED, DONE }

    private final String name;
    private final List<User> members;
    private final List<Card> todo;
    private final List<Card> inProgress;
    private final List<Card> toBeRevised;
    private final List<Card> done;

    public Project(String name, User owner){
        this.name = name;
        this.members = new ArrayList<>();
        this.todo = new ArrayList<>();
        this.inProgress = new ArrayList<>();
        this.toBeRevised = new ArrayList<>();
        this.done = new ArrayList<>();
        this.members.add(owner);
    }

    public String getName() { return name; }

    public boolean addMember(User member){
        if(members.contains(member)) return false;
        return members.add(member);
    }

    public List<User> getMembers(){
        return members;
    }

    public boolean addCard(String name, String description){
        Card card = new Card(name, description);
        if(getCard(name, Section.TODO) != null) return false;
        return todo.add(card);
    }

    public Card getCard(String name){
        for(Card card : todo)
            if(card.getName().equals(name)) return card;

        for(Card card: inProgress)
            if(card.getName().equals(name)) return card;

        for(Card card: toBeRevised)
            if(card.getName().equals(name)) return card;

        for(Card card: done)
            if(card.getName().equals(name)) return card;

        return null;
    }

    public Card getCard(String name, Section section){

        List<Card> list;
        // Enhanced switch in java 13
        switch (section){
            case TODO: list = todo; break;
            case INPROGRESS: list = inProgress; break;
            case TOBEREVISED: list = toBeRevised; break;
            case DONE: list = done; break;
            default: throw new IllegalStateException("Unexpected value: " + section);
        }

        for(Card card : list)
            if(card.getName().equals(name)) return card;

        return null;
    }

    public List<Card> getCards(){
        List<Card> cards = new ArrayList<>(todo);
        cards.addAll(inProgress);
        cards.addAll(toBeRevised);
        cards.addAll(done);
        return cards;
    }

    public String getCardHistory(String name){
        Card card = getCard(name);
        if(card == null) throw new NullPointerException(name);
        return card.getHistory();
    }

    public boolean canDeleteProject(){
        return todo.isEmpty() && inProgress.isEmpty()
                && toBeRevised.isEmpty() && !done.isEmpty();
    }

    private List<Card> getList(Section section){
        switch (section){
            case TODO: return todo;
            case INPROGRESS: return inProgress;
            case TOBEREVISED: return toBeRevised;
            case DONE: return done;
            default: return null;
        }
    }

    @SuppressWarnings("ConstantConditions")
    public void moveCard(String name, Section from, Section destination){
        // vincoli
        if(from == Section.TODO && destination != Section.INPROGRESS) return;
        if(from == Section.INPROGRESS
                && (destination != Section.TOBEREVISED || destination != Section.DONE)) return;
        if(from == Section.TOBEREVISED
                && (destination != Section.INPROGRESS || destination != Section.DONE)) return;
        if(from == Section.DONE) return;

        Card card = getCard(name, from);
        if(card != null){
            getList(destination).add(card);
            getList(from).remove(card);
            // Maybe merge
            card.setSection(destination);
            card.addHistory(destination);
        }
    }
}
