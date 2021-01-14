import WorthExceptions.CardNotFoundException;
import WorthExceptions.IllegalCardMovementException;

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

    private final String multicastIP;
    private final int multicastPort;

    public Project(String name, User owner, String multicastIP, int multicastPort){
        this.name = name;
        this.members = new ArrayList<>();
        this.todo = new ArrayList<>();
        this.inProgress = new ArrayList<>();
        this.toBeRevised = new ArrayList<>();
        this.done = new ArrayList<>();
        this.members.add(owner);
        this.multicastIP = multicastIP;
        this.multicastPort = multicastPort;
    }

    public String getName() { return name; }

    public void addMember(User member){
        if(members.contains(member)) return;
        members.add(member);
    }

    public List<User> getMembers(){
        return members;
    }

    public void addCard(String name, String description){
        Card card = new Card(name, description);
        if(getCard(name, Section.TODO) != null) return;
        todo.add(card);
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

    // TODO CAMBIARE
    public List<Card> getList(Section section){
        switch (section){
            case TODO: return todo;
            case INPROGRESS: return inProgress;
            case TOBEREVISED: return toBeRevised;
            case DONE: return done;
            default: return null;
        }
    }

    public void moveCard(String name, Section fromSection, Section toSection)
            throws IllegalCardMovementException, CardNotFoundException {

        // Controllo se from e to rispettano i vincoli di spostamento delle card
        // * -> *
        if(fromSection == toSection)
            throw new IllegalCardMovementException(fromSection.toString(),toSection.toString());

        // * -> TODO
        if(toSection == Section.TODO)
            throw new IllegalCardMovementException(fromSection.toString(),toSection.toString());

        // DONE -> *
        if(fromSection == Section.DONE)
            throw new IllegalCardMovementException(fromSection.toString(),toSection.toString());

        // TODO -> (TOBEREVISED|DONE)
        if(fromSection == Section.TODO && toSection != Section.INPROGRESS)
            throw new IllegalCardMovementException(fromSection.toString(),toSection.toString());

        Card card = getCard(name, fromSection);
        if(card != null){
            // Tolgo la card dalla lista originaria
            getList(toSection).add(card);
            // La aggiungo a quella di destinazione
            getList(fromSection).remove(card);
            // Aggiorno la lista di appartenenza
            card.setSection(toSection); // setSection aggiorna anche la history
        }else throw new CardNotFoundException(name);
    }

    public boolean canDelete(){
        return todo.isEmpty()
                && inProgress.isEmpty()
                && toBeRevised.isEmpty();
    }

    public String getMulticastInfo(){
        // [projectName,multicastIP,multicastPort]
        return "["+this.name+","+this.multicastIP+","+this.multicastPort+"]";
    }



}
