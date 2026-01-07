package shvalieva.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.lang.Assert;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import shvalieva.entity.User;
import shvalieva.enums.Role;
import shvalieva.repository.UserRepository;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecretKey key;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       @Value("${security.jwt.secret}") String secret) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;

        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        Assert.isTrue(bytes.length >= 32, "JWT secret must be at least 256 bits");
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    public User register(String username, String password, boolean admin) {
        User u = new User();
        u.setUsername(username);
        u.setPassword(passwordEncoder.encode(password));
        u.setRole(admin ? Role.ADMIN : Role.USER);
        return userRepository.save(u);
    }

    public String login(String username, String password) {
        User u = userRepository.findByUsername(username)
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (!passwordEncoder.matches(password, u.getPassword())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        Instant now = Instant.now();
        return Jwts.builder()
                .setSubject(u.getUsername())
                .claim("userId", u.getId())
                .claim("authorities", List.of("ROLE_" + u.getRole().name()))
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusSeconds(3600)))
                .signWith(key)
                .compact();
    }
}
