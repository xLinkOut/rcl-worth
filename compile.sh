#!/bin/bash
# Si generato con Maven due .jar, uno per il client ed uno per il server,
# comprensivi delle dipendenze (Jackson, in questo caso)
mvn clean package
# Per semplicità si spostano nella cartella principale 
# e contestualmente si rinominano per chiarezza
mv target/Server-jar-with-dependencies.jar Server.jar
mv target/Client-jar-with-dependencies.jar Client.jar
# Stampo i comandi da eseguire
echo "Esecuzione del server (CTRL-C per uscire)"
echo "java -jar Server.jar [debug]"
echo "Esecuzione del client (CTRL-C oppure comando <quit> per uscire)"
echo "java -jar Client.jar [debug]"