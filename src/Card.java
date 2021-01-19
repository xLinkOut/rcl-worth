// @author Luca Cirillo (545480)

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

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
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getHistory() { return history; }
    public Project.Section getSection() { return section; }
    
    // Setters
    public void setSection(Project.Section section) {
        this.section = section;
        this.history += section.toString() + "|";
    }

    // toString, usato principalmente da showCard
    public String toString(){
        return
            "- Name > " + this.name + "\n" +
            "- Description > " + this.description + "\n" +
            "- Currently in > " + this.section.toString();
    }
}
