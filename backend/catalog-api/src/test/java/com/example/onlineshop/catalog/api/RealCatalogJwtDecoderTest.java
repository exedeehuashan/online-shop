package com.example.onlineshop.catalog.api;

import static org.assertj.core.api.Assertions.*;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.oauth2.jwt.*;

class RealCatalogJwtDecoderTest {
  static HttpServer server; static RSAKey key, other; static JwtDecoder decoder;
  @BeforeAll static void start() throws Exception {
    key=new RSAKeyGenerator(2048).keyID("catalog").generate();other=new RSAKeyGenerator(2048).keyID("other").generate();
    byte[] body=("{\"keys\":["+key.toPublicJWK().toJSONString()+"]}").getBytes(StandardCharsets.UTF_8);
    server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/jwks",e->{e.sendResponseHeaders(200,body.length);e.getResponseBody().write(body);e.close();});server.start();
    decoder=new CatalogSecurityConfiguration().catalogJwtDecoder("http://127.0.0.1:"+server.getAddress().getPort()+"/jwks","https://issuer.example","shop-api","RS256");
  }
  @AfterAll static void stop(){server.stop(0);}
  @Test void enforcesSignatureIssuerAudienceAndRequiredExpirationAtZeroSkew() throws Exception {
    assertThatCode(()->decoder.decode(token(key,"https://issuer.example",List.of("shop-api"),Instant.now().plusSeconds(60)))).doesNotThrowAnyException();
    for(String token:List.of(token(other,"https://issuer.example",List.of("shop-api"),Instant.now().plusSeconds(60)),token(key,"bad",List.of("shop-api"),Instant.now().plusSeconds(60)),token(key,"https://issuer.example",List.of("bad"),Instant.now().plusSeconds(60)),token(key,"https://issuer.example",null,Instant.now().plusSeconds(60)),token(key,"https://issuer.example",List.of("shop-api"),Instant.now().minusSeconds(1)),token(key,"https://issuer.example",List.of("shop-api"),null))) assertThatThrownBy(()->decoder.decode(token)).isInstanceOf(JwtException.class);
  }
  private static String token(RSAKey signer,String issuer,List<String> aud,Instant exp)throws Exception{var b=new JWTClaimsSet.Builder().issuer(issuer).subject("subject").issueTime(new Date());if(aud!=null)b.audience(aud);if(exp!=null)b.expirationTime(Date.from(exp));var jwt=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signer.getKeyID()).build(),b.build());jwt.sign(new RSASSASigner(signer));return jwt.serialize();}
}
