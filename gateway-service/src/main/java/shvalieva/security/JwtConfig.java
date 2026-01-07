package shvalieva.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import javax.crypto.SecretKey;

@Configuration
public class JwtConfig {

    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder(@Value("${security.jwt.secret:dev-secret-please-change}") String secret) {
        SecretKey key = JwtSecretKeyProvider.getHmacKey(secret);
        return NimbusReactiveJwtDecoder.withSecretKey(key).build();
    }
}
