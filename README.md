JWT example
===

This example shows how to use different private keys and certificates to
authenticate via JWT.

Adapted from <https://github.com/wildfly/quickstart/tree/main/jaxrs-jwt>.

# Build

```shell
mvn clean install
```

# Create the private keys and certificates

```shell
rm -f kid*
openssl genrsa -out kid1.key 3072
openssl req -new -out kid1.csr -sha256 -key kid1.key \
  -subj "/C=IT/ST=BO/L=BO/O=Foo/OU=Bar/CN=localhost/emailAddress=foo@bar.com"
openssl x509 -req -in kid1.csr -days 365 -signkey kid1.key -out kid1.crt -outform PEM

openssl genrsa -out kid2.key 3072
openssl req -new -out kid2.csr -sha256 -key kid2.key \
  -subj "/C=IT/ST=BO/L=BO/O=Foo/OU=Bar/CN=localhost/emailAddress=foo@bar.com"
openssl x509 -req -in kid2.csr -days 365 -signkey kid2.key -out kid2.crt -outform PEM
rm kid*.csr
```

# Install WildFly

Download and start WildFly 31:

```shell
curl https://github.com/wildfly/wildfly/releases/download/31.0.0.Final/wildfly-31.0.0.Final.zip \
  -L -o ~/Downloads/wildfly-31.0.0.Final.zip
rm -rf ~/wildfly-31.0.0.Final
unzip ~/Downloads/wildfly-31.0.0.Final.zip -d ~
~/wildfly-31.0.0.Final/bin/standalone.sh
```

# Configure WildFly for JWT authentication to grant kid 1 access

This configures the JWT token access, authorizing kid 1:

```shell
~/wildfly-31.0.0.Final/bin/jboss-cli.sh -c --file=configure-elytron.cli
```

# Add a public key to the JWT realm

```shell
$ export JWT_PUBLIC_KEY1=`openssl x509 -pubkey -noout -in kid1.crt`
$ ~/wildfly-31.0.0.Final/bin/jboss-cli.sh -c --resolve-parameter-values

[standalone@localhost:9990 /] /subsystem=elytron/token-realm=jwt-realm:map-put(name=jwt, key=key-map, \
  value={1="${env.JWT_PUBLIC_KEY1}"})
[standalone@localhost:9990 /] :reload
[standalone@localhost:9990 /] exit
```

# Build and deploy the app

```shell
mvn clean package wildfly:deploy -f hello-jwt-app
```

# Echoing the claims

```shell
$ TOKEN1=`java -cp "hello-jwt-client/target/*" org.sample.JwtClient kid1.key 1 admin admin`
$ curl -H "Authorization: Bearer $TOKEN1" http://localhost:8080/hello-jwt-app/rest/claims | jq
{
  "sub": "admin",
  "iss": "quickstart-jwt-issuer",
  "aud": [
    "jwt-audience"
  ],
  "groups": [
    "admin"
  ],
  "exp": "2024-02-22T13:38:30Z[UTC]"
}
```

# Verify access denied with kid 2

```shell
$ TOKEN2=`java -cp "hello-jwt-client/target/*" org.sample.JwtClient kid2.key 2 admin admin`
$ curl -H "Authorization: Bearer $TOKEN2" http://localhost:8080/hello-jwt-app/rest/protected
<html><head><title>Error</title></head><body>Unauthorized</body></html>
```

# Verify access granted with kid 1

```shell
$ TOKEN1=`java -cp "hello-jwt-client/target/*" org.sample.JwtClient kid1.key 1 admin admin`
$ curl -H "Authorization: Bearer $TOKEN1" http://localhost:8080/hello-jwt-app/rest/protected | jq
{
  "path": "protected",
  "result": "Hello admin!"
}
```

# Adding kid 2

```shell
$ export JWT_PUBLIC_KEY1=`openssl x509 -pubkey -noout -in kid1.crt`
$ export JWT_PUBLIC_KEY2=`openssl x509 -pubkey -noout -in kid2.crt`

$ ~/wildfly-31.0.0.Final/bin/jboss-cli.sh -c --resolve-parameter-values

[standalone@localhost:9990 /] /subsystem=elytron/token-realm=jwt-realm:map-put(name=jwt, key=key-map, \
  value={1="${env.JWT_PUBLIC_KEY1}", 2="${env.JWT_PUBLIC_KEY2}"})

[standalone@localhost:9990 /] :reload
[standalone@localhost:9990 /] exit

$ curl -H "Authorization: Bearer $TOKEN2" \
  http://localhost:8080/hello-jwt-app/rest/protected | jq
{
  "path": "protected",
  "result": "Hello admin!"
}
```

# Verify access granted or denied by user

```shell
$ TOKEN_ADMIN=`java -cp "hello-jwt-client/target/*" org.sample.JwtClient kid1.key 1 admin admin`
$ TOKEN_CUSTOMER=`java -cp "hello-jwt-client/target/*" org.sample.JwtClient kid1.key 1 customer customer`
$ curl -H "Authorization: Bearer $TOKEN_ADMIN" \
  http://localhost:8080/hello-jwt-app/rest/customer
<html><head><title>Error</title></head><body>Forbidden</body></html>
$ curl -H "Authorization: Bearer $TOKEN_CUSTOMER" \
  http://localhost:8080/hello-jwt-app/rest/protected
<html><head><title>Error</title></head><body>Forbidden</body></html>
$ curl -H "Authorization: Bearer $TOKEN_ADMIN" \
  http://localhost:8080/hello-jwt-app/rest/protected | jq
{
  "path": "protected",
  "result": "Hello admin!"
}
$ curl -H "Authorization: Bearer $TOKEN_CUSTOMER" \
  http://localhost:8080/hello-jwt-app/rest/customer | jq
{
  "path": "customer",
  "result": "Hello customer!"
}
```

# Restore the configuration

```shell
mvn wildfly:undeploy -f hello-jwt-app/
~/wildfly-31.0.0.Final/bin/jboss-cli.sh -c --file=restore-configuration.cli
```
