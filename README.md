JWT example
===

This example shows how to use different private keys and certificates to
authenticate via JWT.

Adapted from <https://github.com/wildfly/quickstart/tree/main/jaxrs-jwt>.

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

Download WildFly 31:

```shell
rm -rf ~/wildfly-31.0.0.Final
curl https://github.com/wildfly/wildfly/releases/download/31.0.0.Final/wildfly-31.0.0.Final.zip \
  -L -o ~/Downloads/wildfly-31.0.0.Final.zip
unzip ~/Downloads/wildfly-31.0.0.Final.zip -d ~
```

Backup the XML configuration:

```shell
cp ~/wildfly-31.0.0.Final/standalone/configuration/standalone.xml \
  ~/wildfly-31.0.0.Final/standalone/configuration/standalone.xml.bk
```

Start WildFly:

```shell
export JWT_PRIVATE_KEY1=`pwd`"/kid1.key"
export JWT_PRIVATE_KEY2=`pwd`"/kid2.key"
~/wildfly-31.0.0.Final/bin/standalone.sh
```

# Set the private key path

Set the private key path:

```shell
~/wildfly-31.0.0.Final/bin/jboss-cli.sh -c
[standalone@localhost:9990 /] /system-property=JwtPrivateKeyPath:add(value="${env.JWT_PRIVATE_KEY1}")
{"outcome" => "success"}
```

# Configure WildFly for JWT authentication

```shell
export JWT_PUBLIC_KEY1=`openssl x509 -pubkey -noout -in kid1.crt`
export JWT_PUBLIC_KEY2=`openssl x509 -pubkey -noout -in kid2.crt`
~/wildfly-31.0.0.Final/bin/jboss-cli.sh -c --resolve-parameter-values \
  --file=configure-elytron.cli
```

# Build and deploy the app

```shell
mvn clean package wildfly:deploy
mvn verify -Pintegration-testing
```

# Restore the configuration

Undeploy the app:

```shell
mvn wildfly:undeploy
```
Unset the private key path:

```shell
~/wildfly-31.0.0.Final/bin/jboss-cli.sh -c
[standalone@localhost:9990 /] /system-property=JwtPrivateKeyPath:remove
{"outcome" => "success"}
```

Stop WildFly:

```shell
rm ~/wildfly-31.0.0.Final/standalone/configuration/standalone.xml && \
  cp ~/wildfly-31.0.0.Final/standalone/configuration/standalone.xml.bk \
  ~/wildfly-31.0.0.Final/standalone/configuration/standalone.xml
```

or:

```shell
~/wildfly-31.0.0.Final/bin/jboss-cli.sh -c --file=restore-configuration.cli
```
