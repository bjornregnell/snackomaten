VERSION=0.2.0
SCALAVERSION=3.6.4
mkdir -p ~/snackomaten
mkdir -p ~/snackomaten/bin
sbt --client "server/assembly"
cp server/target/scala-$SCALAVERSION/snackomatenServer-assembly-$VERSION.jar ~/snackomaten/bin/snackomaten-server.jar
