sbt assembly
scp target/scala-2.13/subfinder.jar mhicks@fileserver:~/subfinder.jar
echo Run sudo cp ~/subfinder.jar /usr/bin/subfinder.jar