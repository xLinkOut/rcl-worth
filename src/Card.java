// @author Luca Cirillo (545480)

// Jackson
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

// Card con nome e descrizione, associata ad un progetto
public class Card {

    private final String name;        // Nome della Card, univoco nell'ambito del progetto
    private final String description; // Descrizione della Card
    private String history;           // Storia dei movimenti, definita come Section1|...|SectionN
    private Project.Section section;  // Riferimento alla lista in cui si trova attualmente la Card

    @JsonCreator
    public Card(
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("history") String history,
            @JsonProperty("section") String section){

        this.name = name;
        this.description = description;
        this.history = history;
        this.section = Project.Section.valueOf(section);
    }

    public Card(String name, String description){
        this.name = name;
        this.description = description;
        this.section = Project.Section.TODO;
        this.history = Project.Section.TODO.toString() + "|";
    }
    
    // Getters
    public String getName() { return this.name; }
    public String getDescription() { return this.description; }
    public String getHistory() { return this.history; }
    public Project.Section getSection() { return this.section; }
    
    // Setters
    public void setSection(Project.Section section) {
        this.section = section;
        // Aggiorna la storia dei movimenti della card
        this.history += section.toString() + "|";
    }

    // Usato da showCard
    public String toString(){
        return
            "- Name > " + this.name + "\n" +
            "- Description > " + this.description + "\n" +
            "- Currently in > " + this.section.toString();
    }
}
