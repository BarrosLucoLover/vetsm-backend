# syntax=docker/dockerfile:1
# Una sola receta para los cuatro microservicios: el argumento MODULO indica cuál se construye
# (ms-usuarios, ms-catalogo, ms-ventas o ms-agenda). docker-compose.yml la usa cuatro veces.

# ---------- Etapa 1: compilar con Maven ----------
FROM maven:3.9.9-eclipse-temurin-17 AS construccion
WORKDIR /app
# Si un antivirus o proxy revisa el HTTPS, su certificado raíz va en certificados-extra/*.crt
# (ver certificados-extra/LEEME.md). Sin certificados, este paso no hace nada.
COPY certificados-extra/ /tmp/certificados-extra/
RUN for c in /tmp/certificados-extra/*.crt; do \
        [ -f "$c" ] || continue; \
        keytool -importcert -noprompt -cacerts -storepass changeit -alias "$(basename "$c" .crt)" -file "$c"; \
    done
COPY pom.xml .
COPY comun comun
COPY ms-usuarios ms-usuarios
COPY ms-catalogo ms-catalogo
COPY ms-ventas ms-ventas
COPY ms-agenda ms-agenda
ARG MODULO
# -am compila también el módulo común. La caché de ~/.m2 se comparte entre los cuatro servicios,
# así las dependencias se descargan una sola vez. Las pruebas se corren aparte (mvnw test).
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -pl ${MODULO} -am package -DskipTests

# ---------- Etapa 2: imagen final, solo con Java y el .jar ----------
FROM eclipse-temurin:17-jre
ARG MODULO
WORKDIR /app
# El servicio no corre como administrador del contenedor.
RUN groupadd --system vetsm && useradd --system --gid vetsm vetsm
COPY --from=construccion /app/${MODULO}/target/${MODULO}.jar app.jar
# El mismo certificado del antivirus también hace falta al ejecutar: la conexión cifrada con
# Oracle pasa por él. El driver de Oracle solo confía en el wallet, salvo que se le indique otro
# almacén; por eso, si hay certificados extra, se le indica el de Java (que ahora los incluye).
# Sin certificados (como en el servidor), este paso no hace nada.
COPY certificados-extra/ /tmp/certificados-extra/
RUN : > /app/opciones-java; \
    for c in /tmp/certificados-extra/*.crt; do \
        [ -f "$c" ] || continue; \
        keytool -importcert -noprompt -cacerts -storepass changeit -alias "$(basename "$c" .crt)" -file "$c"; \
        echo "-Djavax.net.ssl.trustStore=$JAVA_HOME/lib/security/cacerts -Djavax.net.ssl.trustStorePassword=changeit" > /app/opciones-java; \
    done; rm -rf /tmp/certificados-extra
USER vetsm
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS $(cat /app/opciones-java) -jar app.jar"]
