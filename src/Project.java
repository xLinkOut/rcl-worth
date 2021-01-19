// @author Luca Cirillo (545480)

import java.util.List;
import java.util.ArrayList;

// Jackson
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JacksonInject;

import WorthExceptions.CardNotFoundException;
import WorthExceptions.IllegalCardMovementException;

// Identifica un progetto creato sul sistema da un utente
public class Project {

    // Liste in cui si possono collocare le cards
    public enum Section { TODO, INPROGRESS, TOBEREVISED, DONE }

    private final String name;            // Nome del progetto, univoco nel sistema
    private final List<String> members;   // Utenti membri del progetto

    private final List<Card> todo;        // Cards nella lista TODO
    private final List<Card> inProgress;  // Cards nella lista INPROGRESS
    private final List<Card> toBeRevised; // Cards nella lista TOBEREVISED
    private final List<Card> done;        // Cards nella lista DONE

    private final String multicastIP;     // Indirizzo IP multicast della chat di progetto
    private final int multicastPort;      // Porta della chat di progetto

    @JsonCreator // Costruttore di Jackson
    public Project(
            @JsonProperty("name") String name,
            @JsonProperty("members") List<String> members,
            @JsonProperty("multicastIP") String multicastIP,
            @JsonProperty("multicastPort") int multicastPort,
            @JacksonInject("cards") List<Card> cards){

        this.name = name;
        this.members = members;
        this.multicastIP = multicastIP;
        this.multicastPort = multicastPort;
        this.todo = new ArrayList<>();
        this.inProgress = new ArrayList<>();
        this.toBeRevised = new ArrayList<>();
        this.done = new ArrayList<>();
        for(Card card : cards) getList(card.getSection()).add(card);
    }

    public Project(String name, String owner, String multicastIP, int multicastPort){
        this.name = name;
        this.members = new ArrayList<>();
        this.members.add(owner);
        this.todo = new ArrayList<>();
        this.inProgress = new ArrayList<>();
        this.toBeRevised = new ArrayList<>();
        this.done = new ArrayList<>();
        this.multicastIP = multicastIP;
        this.multicastPort = multicastPort;
    }

    // Getters
    public String getName() { return this.name; }

    public List<String> getMembers(){ return this.members; }

    // Cerca una card nel progetto quando non si conosce la lista di appartenenza
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

    // Cerca una card nel progetto in una specifica lista
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

    public String getCardHistory(String name){
        Card card = getCard(name);
        if(card == null) throw new NullPointerException(name);
        return card.getHistory();
    }

    public String getMulticastIP(){ return this.multicastIP; }

    public int getMulticastPort() { return this.multicastPort; }

    @JsonIgnore // E' solo una scorciatoia per altri metodi, non deve persistere sul sistema
    public String getMulticastInfo(){ return "["+this.name+","+this.multicastIP+","+this.multicastPort+"]"; }

    public List<Card> getList(Section section){
        // TODO CAMBIARE
        switch (section){
            case TODO: return todo;
            case INPROGRESS: return inProgress;
            case TOBEREVISED: return toBeRevised;
            case DONE: return done;
            default: return null;
        }
    }

    // Aggiunge un nuovo utente come membro del progetto
    public void addMember(String member){ if(!members.contains(member)) members.add(member); }

    // Aggiunge una nuova card al progetto, che finisce nella lista TODO
    public Card addCard(String name, String description){
        Card card = new Card(name, description);
        // TODO: throw eccezione per card già esistente già presente in server addcard
        //if(getCard(name, Section.TODO) != null) return null;
        todo.add(card);
        return card;
    }

    // Sposta la card da una lista ad un'altra
    public Card moveCard(String name, Section fromSection, Section toSection)
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
        return card;
    }

    // Determina se un progetto può essere cancellato o meno
    public boolean canDelete(){
        return todo.isEmpty()
                && inProgress.isEmpty()
                && toBeRevised.isEmpty();
    }

}
