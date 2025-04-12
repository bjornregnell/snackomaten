# snackomaten

A minimalistic client-server for students nothing at all like discord or canvas.

## How to run locally

3. Build the server and check the file name of output jar

4. Run the server with correct versions of scala and assembly, something similar to:

1. Build the client and check the file name of output jar
    ```
    sbtn client/assembly
    ```

2. In another terminal: run the client with correct versions of scala and assembly, something similar to: 
    ```
    java -jar client/target/scala-3.6.4/snackomatenClient-assembly-0.4.0.jar localhost
    ```

4. In another terminal: run yet another client with the same command as above.

5. Happy chatting!