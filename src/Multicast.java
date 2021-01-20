// @author Luca Cirillo (545480)

import java.util.Queue;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.stream.Collectors;

// Jackson
import com.fasterxml.jackson.annotation.JsonIgnore;

// Eccezioni
import WorthExceptions.NoMulticastAddressAvailableException;

// Mantiene le informazioni sugli indirizzi IP multicast assegnati e rilasciati
public class Multicast {

    // Ultimo indirizzo IP assegnato ad un progetto
    // (valore iniziale su 224.0.0.0, che ovviamente non viene mai assegnato)
    private String lastIP;
    // Coda di indirizzi IP precedentemente assegnati a progetti
    // e poi rilasciati in seguito alla loro cancellazione
    private Queue<String> releasedIP;

    // Inizializza un gestore "pulito" degli indirizzi multicast
    public Multicast(){
        this.lastIP = "224.0.0.0";
        this.releasedIP = new LinkedList<>();
    }

    // Getters per Jackson
    public String getLastIP(){return this.lastIP;}
    public Queue<String> getReleasedIP(){return this.releasedIP;}

    // Aggiunge un nuovo IP alla coda di indirizzi IP rilasciati
    public void addReleasedIP(String IP){
        this.releasedIP.add(IP);
    }

    @JsonIgnore
    // Genera un nuovo indirizzo IP oppure ne ritorna uno attualmente libero
    public String getNewIP()
            throws NoMulticastAddressAvailableException {
        // Se è presente un indirizzo IP precedentemente generato
        // ma rilasciato a seguito della cancellazione del progetto
        // a cui era stato assegnato, lo riassegno togliendolo
        // dalla coda degli indirizzi rilasciati
        if(!releasedIP.isEmpty()) return releasedIP.poll();

        // Altrimenti, provvedo alla generazione di un nuovo indirizzo
        // Range possibile: 224.0.0.1 -> 239.255.255.255

        // Controllo che il sistema non sia arrivato all'ultimo IP disponibile (239.255.255.255)
        // Nel caso, vuol dire che tutti gli indirizzi IP precedentemente generati sono ancora in uso,
        // di conseguenza lancio un'eccezione per impedire la creazione del progetto
        // (non è previsto un reset degli indirizzi, in quanto se è stato raggiunto il limite
        // ma la coda degli indirizzi rilasciati è vuota, vuol dire che sono tutti in uso)
        if(lastIP.equals("239.255.255.255"))
            throw new NoMulticastAddressAvailableException();

        // Altrimenti, genero un nuovo indirizzo incrementale partendo dall'ultimo utilizzato
        else {
            // Divido gli ottetti separati dal punto (.) e li casto da String a int
            int[] arrayLastIP = Arrays.stream(
                    lastIP.split("\\."))
                    .mapToInt(Integer::parseInt).toArray();
            // Li parso e li incremento gradualmente
            for (int i=3;i>=0;i--) {
                if(i>0){
                    // I 3 byte meno significativi hanno limite massimo 255
                    if (arrayLastIP[i] < 255) { arrayLastIP[i]++; break; }
                }else{
                    // Il byte più significativo invece 239
                    if (arrayLastIP[i] < 239) { arrayLastIP[i]++; }
                }
            }
            // Riporto gli ottetti da int a String, in notazione decimale puntata
            // e aggiorno il valore di lastIP della classe Multicast
            lastIP = Arrays.stream(arrayLastIP)
                    .mapToObj(String::valueOf)
                    .collect(Collectors.joining("."));

            // Ritorno infine l'indirizzo IP generato
            return lastIP;
        }
    }

}
