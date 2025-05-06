# snackomaten

A minimalistic client-server for students nothing at all like discord or canvas.

## Dependencies

* Java >=21: `sdk install java 21.0.6-tem` 
* Scala >= 3.7.0-RC4: `sdk install scala` https://scala-lang.org/download/
* sbt >= 1.10: `sdk install sbt` https://www.scala-sbt.org/download
* keyring or similar to store master password: `sudo apt install python3-keyring`

## How to build and run locally

1. Build the server and check the file name of output jar
    ```
    sbtn server/assembly
    ```

2. Run the server with correct versions of scala and assembly, something similar to:
    ```
    java -jar client/target/scala-3.6.4/snackomatenClient-assembly-0.4.0.jar 8090 serverpwd
    ```
  If you are on linux and use `keyring` then you can `keyring set snackomaten server` and enter a secure password and replace the second server argument with `$(keyring get snackomaten server)` see file ´start-server.sh`

3. Build the client and check the file name of output jar
    ```
    sbtn client/assembly
    ```

4. In another terminal: run the client on the same machine as the server, something similar to: 
    ```
    java -jar client/target/scala-3.7.0-RC4/snackomatenClient-assembly-0.4.0.jar --host localhost --password pwd  --dir testUser2 --pid 20010101-9999
    ```
  If you are on linux and use `keyring` then you can `keyring set snackomaten testUser2` and enter a secure password and then replace pwd with `$(keyring get snackomaten testUser2)` see file `start-client localhost.sh`

5. In another terminal: run yet another client with the same command as above.

6. Happy chatting!