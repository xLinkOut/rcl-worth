// @author Luca Cirillo (545480)

import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.ArrayList;

// Jackson
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JacksonInject;

// Eccezioni di WORTH
import WorthExceptions.CardAlreadyExists;
import WorthExceptions.CardNotFoundException;
import WorthExceptions.IllegalCardMovementException;

// Progetto creato sul sistema da un utente
public class Project {

    // Liste in cui si possono collocare le cards
    public enum Section { TODO, INPROGRESS, TOBEREVISED, DONE }

    private final String name;            // Nome del progetto, univoco nel sistema
    private final List<String> members;   // Utenti che sono membri del progetto

    private final List<Card> todo;        // Cards nella lista TODO
    private final List<Card> inProgress;  // Cards nella lista INPROGRESS
    private final List<Card> toBeRevised; // Cards nella lista TOBEREVISED
    private final List<Card> done;        // Cards nella lista DONE
    private final String multicastIP;     // Indirizzo IP multicast della chat di progetto
    private final int multicastPort;      // Porta della chat di progetto
    @JsonIgnore
    private final Map<Section, List<Card>> lists; // Dizionario per recuperare facilmente la lista desiderata

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

        // Inizializzo la map per le liste
        this.lists = new HashMap<>();
        lists.put(Section.TODO, this.todo);
        lists.put(Section.INPROGRESS, this.inProgress);
        lists.put(Section.TOBEREVISED, this.toBeRevised);
        lists.put(Section.DONE, this.done);

        // Smistamento delle cards nelle rispettive liste all'avvio del server
        for(Card card : cards) lists.get(card.getSection()).add(card);
    }

    public Project(String name, String owner, String multicastIP, int multicastPort){
        this.name = name;
        this.members = new ArrayList<>();
        this.members.add(owner);

        this.todo = new ArrayList<>();
        this.toBeRevised = new ArrayList<>();
        this.inProgress = new ArrayList<>();
        this.done = new ArrayList<>();

        // Inizializzo la map per le liste
        this.lists = new HashMap<>();
        lists.put(Section.TODO, this.todo);
        lists.put(Section.INPROGRESS, this.inProgress);
        lists.put(Section.TOBEREVISED, this.toBeRevised);
        lists.put(Section.DONE, this.done);

        this.multicastIP = multicastIP;
        this.multicastPort = multicastPort;
    }

    // Getters
    public String getName() { return this.name; }

    public List<String> getMembers(){ return this.members; }

    // Cerca una card nel progetto quando non si conosce la lista di appartenenza
    public Card getCard(String name) throws CardNotFoundException {
        for(List<Card> list : lists.values())
            for(Card card : list)
                if(card.getName().equals(name))
                    return card;
        throw new CardNotFoundException(name);
    }

    // Cerca una card nel progetto in una lista specifica
    public Card getCard(String name, Section section) throws CardNotFoundException {
        for(Card card : lists.get(section))
            if(card.getName().equals(name))
                return card;
        throw new CardNotFoundException(name);
    }

    public String getCardHistory(String name) throws CardNotFoundException {
        // Se la card non viene trovata, la getCard lancia una CardNotFoundException
        Card card = getCard(name);
        return card.getHistory();
    }

    public String getMulticastIP(){ return this.multicastIP; }

    public int getMulticastPort(){ return this.multicastPort; }

    @JsonIgnore // E' una scorciatoia per altri metodi, non deve persistere sul sistema
    public String getMulticastInfo(){
        // [projectName,multicastIP,multicastPort]
        return "["+this.name+","+this.multicastIP+","+this.multicastPort+"]";
    }

    @JsonIgnore // E' una scorciatoia per altri metodi, non deve persistere sul sistema
    public List<Card> getList(Section section){
        return lists.get(section);
    }

    // Aggiunge un nuovo utente come membro del progetto
    public void addMember(String member){
        if(!members.contains(member)) members.add(member);
    }

    // Verifica se un utente è membro del progetto
    public boolean isMember(String username){
        return this.members.contains(username);
    }

    // Aggiunge una nuova card al progetto, che finisce nella lista TODO di default
    public Card addCard(String name, String description) throws CardAlreadyExists {
        // Controllare che non ci sia già una card con lo stesso nome
        try { getCard(name); throw new CardAlreadyExists(name);
        } catch (CardNotFoundException ignored) {}
        // Creo e aggiungo la nuova card
        Card card = new Card(name, description);
        todo.add(card);
        return card;
    }

    // Sposta la card da una lista ad un'altra
    public Card moveCard(String name, Section fromSection, Section toSection)
            throws CardNotFoundException, IllegalCardMovementException {

        // Controllo se <from> e <to> rispettano i vincoli di spostamento delle cards
        // * -> * (Spostamento nella stessa lista di partenza)
        if(fromSection == toSection)
            throw new IllegalCardMovementException(fromSection.toString(),toSection.toString());

        // * -> TODO (Spostamento nella lista TODO)
        if(toSection == Section.TODO)
            throw new IllegalCardMovementException(fromSection.toString(),toSection.toString());

        // DONE -> * (Spostamento dalla lista DONE)
        if(fromSection == Section.DONE)
            throw new IllegalCardMovementException(fromSection.toString(),toSection.toString());

        // TODO -> (TOBEREVISED|DONE) (Spostamento da TODO a TOBEREVISED oppure DONE)
        if(fromSection == Section.TODO && toSection != Section.INPROGRESS)
            throw new IllegalCardMovementException(fromSection.toString(),toSection.toString());

        Card card = getCard(name, fromSection);
        if(card == null) throw new CardNotFoundException(name);

        // Tolgo la card dalla lista originaria
        lists.get(toSection).add(card);
        // La aggiungo a quella di destinazione
        lists.get(fromSection).remove(card);
        // Aggiorno la lista di appartenenza
        card.setSection(toSection); // setSection aggiorna anche la history
        return card;
    }

    // Determina se un progetto può essere cancellato o meno
    public boolean canDelete(){
        return todo.isEmpty()
                && inProgress.isEmpty()
                && toBeRevised.isEmpty();
    }

}
