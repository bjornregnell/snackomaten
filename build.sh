VERSION=0.1.0
sbt "server/assembly"
cp server/target/scala-3.6.4/SpamvasServer-assembly-$VERSION.jar spamvas-server.jar
