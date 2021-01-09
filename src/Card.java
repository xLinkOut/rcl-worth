
public class Card {



    public enum Status { TODO, INPROGRESS, TOBEREVISED, DONE };

    private final String name;
    private final String caption;
    private Status status;

    public Card(String name, String caption){
        this.name = name;
        this.caption = caption;
        this.status = Status.TODO;
    }

    public String getName() {
        return name;
    }

    public String getCaption() {
        return caption;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }
}
