package com.example.onlineshop.identity.api;

import static org.assertj.core.api.Assertions.*;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.oauth2.jwt.*;

class RealJwtDecoderTest {
  static HttpServer server; static RSAKey key; static RSAKey other; static JwtDecoder decoder;
  static final String ISSUER="https://issuer.example", AUDIENCE="shop-api";
  @BeforeAll static void start() throws Exception {
    key=new RSAKeyGenerator(2048).keyID("key").generate(); other=new RSAKeyGenerator(2048).keyID("other").generate();
    byte[] jwks=("{\"keys\":["+key.toPublicJWK().toJSONString()+"]}").getBytes(StandardCharsets.UTF_8);
    server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0); server.createContext("/jwks", e->{e.sendResponseHeaders(200,jwks.length);e.getResponseBody().write(jwks);e.close();});server.start();
    decoder=new OidcSecurityConfiguration().jwtDecoder("http://127.0.0.1:"+server.getAddress().getPort()+"/jwks",ISSUER,AUDIENCE,"RS256");
  }
  @AfterAll static void stop(){server.stop(0);}
  @Test void acceptsOnlyCorrectSignatureIssuerAudienceAlgorithmAndTimes() throws Exception {
    assertThatCode(()->decoder.decode(sign(key,claims(ISSUER,List.of(AUDIENCE),Instant.now().plusSeconds(300),null)))).doesNotThrowAnyException();
    assertRejected(sign(other,claims(ISSUER,List.of(AUDIENCE),Instant.now().plusSeconds(300),null)));
    assertRejected(sign(key,claims("https://evil.example",List.of(AUDIENCE),Instant.now().plusSeconds(300),null)));
    assertRejected(sign(key,claims(ISSUER,List.of("other"),Instant.now().plusSeconds(300),null)));
    assertRejected(sign(key,claims(ISSUER,null,Instant.now().plusSeconds(300),null)));
    assertRejected(sign(key,claims(ISSUER,List.of(AUDIENCE),Instant.now().minusSeconds(1),null)));
    assertRejected(sign(key,claims(ISSUER,List.of(AUDIENCE),Instant.now().plusSeconds(300),Instant.now().plusSeconds(60))));
    SignedJWT hs=new SignedJWT(new JWSHeader(JWSAlgorithm.HS256),claims(ISSUER,List.of(AUDIENCE),Instant.now().plusSeconds(300),null));hs.sign(new MACSigner("01234567890123456789012345678901"));assertRejected(hs.serialize());
  }
  @Test void requiresExpirationClaim() throws Exception { assertRejected(sign(key,claims(ISSUER,List.of(AUDIENCE),null,null))); }
  private static void assertRejected(String token){assertThatThrownBy(()->decoder.decode(token)).isInstanceOf(JwtException.class);}
  private static JWTClaimsSet claims(String iss,List<String> aud,Instant exp,Instant nbf){var b=new JWTClaimsSet.Builder().issuer(iss).subject("subject-1").issueTime(new Date());if(aud!=null)b.audience(aud);if(exp!=null)b.expirationTime(Date.from(exp));if(nbf!=null)b.notBeforeTime(Date.from(nbf));return b.build();}
  private static String sign(RSAKey signer,JWTClaimsSet claims)throws Exception{var jwt=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signer.getKeyID()).build(),claims);jwt.sign(new RSASSASigner(signer));return jwt.serialize();}
}
