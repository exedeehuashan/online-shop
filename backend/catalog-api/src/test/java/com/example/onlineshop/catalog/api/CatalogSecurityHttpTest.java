package com.example.onlineshop.catalog.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers @SpringBootTest @AutoConfigureMockMvc
class CatalogSecurityHttpTest {
  @Container static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:16.6-alpine");
  static final RSAKey KEY; static final HttpServer JWKS, IDENTITY; static volatile String deniedToken;
  static {
    try {
      KEY=new RSAKeyGenerator(2048).keyID("catalog-http").generate();
      byte[] jwks=("{\"keys\":["+KEY.toPublicJWK().toJSONString()+"]}").getBytes(StandardCharsets.UTF_8);
      JWKS=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);JWKS.createContext("/jwks",e->{e.sendResponseHeaders(200,jwks.length);e.getResponseBody().write(jwks);e.close();});JWKS.start();
      IDENTITY=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);IDENTITY.createContext("/api/v1/me",e->{String auth=e.getRequestHeaders().getFirst("Authorization");String permissions=auth!=null&&auth.equals("Bearer "+deniedToken)?"[]":"[\"catalog:write\"]";byte[] body=("{\"id\":\"00000000-0000-0000-0000-000000000001\",\"displayName\":\"test\",\"permissions\":"+permissions+"}").getBytes(StandardCharsets.UTF_8);e.getResponseHeaders().add("Content-Type","application/json");e.sendResponseHeaders(200,body.length);e.getResponseBody().write(body);e.close();});IDENTITY.start();
    } catch(Exception failure){throw new ExceptionInInitializerError(failure);}
  }
  @AfterAll static void stop(){JWKS.stop(0);IDENTITY.stop(0);}
  @DynamicPropertySource static void properties(DynamicPropertyRegistry r){
    r.add("spring.datasource.url",postgres::getJdbcUrl);r.add("spring.datasource.username",postgres::getUsername);r.add("spring.datasource.password",postgres::getPassword);
    r.add("catalog.security.enabled",()->"true");r.add("catalog.security.issuer",()->"https://issuer.example");r.add("catalog.security.audience",()->"shop-api");
    r.add("catalog.security.jwk-set-uri",()->"http://127.0.0.1:"+JWKS.getAddress().getPort()+"/jwks");r.add("catalog.security.identity-base-url",()->"http://127.0.0.1:"+IDENTITY.getAddress().getPort());
  }
  @Autowired MockMvc mvc;
  @Test void publicReadsRemainPublicAndMutationsRequireEffectiveCatalogWrite() throws Exception {
    mvc.perform(get("/api/v1/products")).andExpect(status().isOk());
    mvc.perform(get("/api/v2/products")).andExpect(status().isOk()).andExpect(header().exists("X-Correlation-ID"));
    mvc.perform(post("/api/v1/products").header("X-Correlation-ID","catalog-test").contentType("application/json").content(product()))
        .andExpect(status().isUnauthorized()).andExpect(content().contentType("application/problem+json"))
        .andExpect(header().string("WWW-Authenticate","Bearer")).andExpect(header().string("X-Correlation-ID","catalog-test"));
    mvc.perform(post("/api/v1/products").header("Authorization","Bearer "+token("allowed")).contentType("application/json").content(product())).andExpect(status().isCreated());
    deniedToken=token("denied");
    mvc.perform(post("/api/v1/products").header("Authorization","Bearer "+deniedToken).contentType("application/json").content(product())).andExpect(status().isForbidden()).andExpect(content().contentType("application/problem+json"));
  }
  private static String product(){return "{\"name\":\"Secure Tea\",\"description\":\"Fixture\",\"price\":\"12.30\",\"currency\":\"NZD\",\"stockQuantity\":4}";}
  private static String token(String subject)throws Exception{Instant now=Instant.now();var claims=new JWTClaimsSet.Builder().issuer("https://issuer.example").subject(subject).audience("shop-api").issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(60))).build();var jwt=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY.getKeyID()).build(),claims);jwt.sign(new RSASSASigner(KEY));return jwt.serialize();}
}
