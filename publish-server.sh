VERSION=0.2.0
SCALAVERSION=3.6.4
sbt --client "server/assembly"
scp server/target/scala-$SCALAVERSION/snackomatenServer-assembly-$VERSION.jar $SNACKUSER@$SNACKSERVER:/home/$SNACKUSER/snackomaten/bin/snackomaten-server.jar