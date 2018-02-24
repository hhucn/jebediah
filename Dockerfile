FROM clojure
COPY ./project.clj .
RUN lein deps
COPY . .
RUN lein clean && lein ring uberjar

FROM openjdk:8-jre-alpine
ENV PORT 1234

COPY --from=0 /tmp/target/jebediah-0.2.0-standalone.jar jebediah-0.2.0-standalone.jar


EXPOSE $PORT
CMD ["java", "-jar", "jebediah-0.2.0-standalone.jar"]