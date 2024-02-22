package org.sample;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.List;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.RSASSASigner;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObjectBuilder;

public class JwtClient {
    private final PrivateKey privateKey;
    private final String kid;

    public JwtClient(PrivateKey privateKey, String kid) {
        this.privateKey = privateKey;
        this.kid = kid;
    }

    public static void main(String[] args) throws Exception {
        String pkPath = args[0];
        String kid = args[1];
        String user = args[2];
        String[] roles = args[3].split(",");
        PrivateKey privateKey = loadPrivateKeyFromPem(pkPath);
        JwtClient client = new JwtClient(privateKey, kid);
        String jwt = client.createJwt(user, roles);
        System.out.println(jwt);
    }

    private static PrivateKey loadPrivateKeyFromPem(final String fileName)
    throws Exception {
        try (InputStream is = new FileInputStream(fileName)) {
            byte[] contents = new byte[4096];
            int length = is.read(contents);
            String rawKey = new String(contents, 0, length, StandardCharsets.UTF_8)
                    .replaceAll("-----BEGIN (.*)-----", "")
                    .replaceAll("-----END (.*)----", "")
                    .replaceAll("\r\n", "").replaceAll("\n", "")
                    .trim();

            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(Base64.getDecoder().decode(rawKey));
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");

            return keyFactory.generatePrivate(keySpec);
        }
    }

    private static final int TOKEN_VALIDITY = 14400;
    private static final String ISSUER = "quickstart-jwt-issuer";
    private static final String AUDIENCE = "jwt-audience";

    public String createJwt(final String subject, final String[] roles) throws Exception {
        final JWSSigner signer = new RSASSASigner(privateKey);
        final JsonArrayBuilder rolesBuilder = Json.createArrayBuilder(List.of(roles));
        final JsonObjectBuilder claimsBuilder = Json.createObjectBuilder()
                .add("sub", subject)
                .add("iss", ISSUER)
                .add("aud", AUDIENCE)
                .add("groups", rolesBuilder.build())
                .add("exp", ((System.currentTimeMillis() / 1000) + TOKEN_VALIDITY));

        final JWSObject jwsObject = new JWSObject(new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(kid)
                .type(new JOSEObjectType("jwt")).build(),
                new Payload(claimsBuilder.build().toString()));

        jwsObject.sign(signer);

        return jwsObject.serialize();
    }
}
