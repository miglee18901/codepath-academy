package com.example.java_basic.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.util.Date;
import java.util.function.Function;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

  // Lấy chuỗi secret từ file application.properties, nếu không có sẽ lấy chuỗi
  // mặc định phía sau dấu hai chấm
  @Value(
    "${jwt.secret:DayLaMotChuoiBiMatSieuDaiCuaHeThongQuanLyCauLongCanPhaiBaoMatTuyetDoi}"
  )
  private String secretKey;

  @Value("${jwt.expiration:900000}")
  private long expiration;

  public String generateToken(UserDetails userDetails) {
    return Jwts.builder()
      .id(java.util.UUID.randomUUID().toString())
      .subject(userDetails.getUsername())
      .issuedAt(new Date(System.currentTimeMillis()))
      .expiration(new Date(System.currentTimeMillis() + expiration)) // Token sống 15 phút
      .signWith(getSignInKey())
      .compact();
  }

  public String extractUsername(String token) {
    return extractClaim(token, Claims::getSubject);
  }

  public boolean isTokenValid(String token, UserDetails userDetails) {
    final String username = extractUsername(token);
    return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
  }

  private boolean isTokenExpired(String token) {
    return extractExpiration(token).before(new Date());
  }

  private Date extractExpiration(String token) {
    return extractClaim(token, Claims::getExpiration);
  }

  public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
    final Claims claims = Jwts.parser()
      .verifyWith(getSignInKey())
      .build()
      .parseSignedClaims(token)
      .getPayload();
    return claimsResolver.apply(claims);
  }

  private SecretKey getSignInKey() {
    byte[] keyBytes = Decoders.BASE64.decode(secretKey);
    return Keys.hmacShaKeyFor(keyBytes);
  }
}
