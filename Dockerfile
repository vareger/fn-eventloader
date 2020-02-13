FROM java:8-jre
ADD ./target/eventloader-1.0.10.jar app.jar
ADD ./target/dependency-jars dependency-jars
VOLUME /tmp
VOLUME /target
RUN bash -c 'touch /app.jar'
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/app.jar"]