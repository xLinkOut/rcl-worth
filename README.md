# RCL-WORTH 🌐
Final project for 'Computer Networks' curse @ UniPi. More info in `relazione/Relazione.pdf`.

## How to run
Run `bash compile.sh`, or follow these steps:
```bash
mvn clean package
mv target/Server-jar-with-dependencies.jar Server.jar
mv target/Client-jar-with-dependencies.jar Client.jar
# Server (CTRL-C to exit)
java -jar Server.jar [debug]
# Client (CTRL-C or 'quit' command to exit) 
java -jar Client.jar [debug]
```
