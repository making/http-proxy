# HTTP ProxyZZ

```
./mvnw clean package -DskipTests=true
java -jar target/http-proxy-0.0.1-SNAPSHOT.jar
```


## Change downstream url

```
DOWNSTREAM_URL=https://www.yahoo.co.jp java -jar target/http-proxy-0.0.1-SNAPSHOT.jar
```

```
curl -H "Host: www.yahoo.co.jp" localhost:8080 -v
```

## Dump server request


```
SERVER_LOG_LEVEL=DEBUG DOWNSTREAM_URL=https://www.yahoo.co.jp java -jar target/http-proxy-0.0.1-SNAPSHOT.jar
```

## Dump client request

```
CLIENT_LOG_LEVEL=DEBUG DOWNSTREAM_URL=https://www.yahoo.co.jp java -jar target/http-proxy-0.0.1-SNAPSHOT.jar
```
