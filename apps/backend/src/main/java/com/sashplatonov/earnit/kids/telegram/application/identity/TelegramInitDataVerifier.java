package com.sashplatonov.earnit.kids.telegram.application.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.sashplatonov.earnit.kids.telegram.config.TelegramConfig;
import com.sashplatonov.earnit.kids.util.TimeProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@ApplicationScoped
public class TelegramInitDataVerifier {
  private static final String WEB_APP_DATA = "WebAppData";

  private final TelegramConfig config;
  private final ObjectReader userReader;
  private final TimeProvider timeProvider;

  @Inject
  public TelegramInitDataVerifier(
      TelegramConfig config, ObjectMapper objectMapper, TimeProvider timeProvider) {
    this.config = config;
    this.userReader = objectMapper.reader();
    this.timeProvider = timeProvider;
  }

  public Optional<VerifiedInitData> verify(String rawInitData) {
    if (rawInitData == null || rawInitData.isBlank() || config.botToken().isEmpty()) {
      return Optional.empty();
    }
    try {
      Map<String, String> values = parse(rawInitData);
      if (!hasValidHash(values)) {
        return Optional.empty();
      }
      if (!signatureMatches(values)) {
        return Optional.empty();
      }
      long authDate = authDate(values);
      if (!isFresh(authDate)) {
        return Optional.empty();
      }
      JsonNode user = user(values);
      long userId = userId(user);
      if (userId < 0) {
        return Optional.empty();
      }
      return Optional.of(
          new VerifiedInitData(
              userId, Instant.ofEpochSecond(authDate), text(user, "username"), displayName(user)));
    } catch (Exception exception) {
      return Optional.empty();
    }
  }

  private boolean hasValidHash(Map<String, String> values) {
    String hash = values.get("hash");
    return hash != null && hash.matches("[0-9a-fA-F]{64}");
  }

  private boolean signatureMatches(Map<String, String> values) throws Exception {
    String receivedHash = values.remove("hash");
    String dataCheckString =
        values.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> entry.getKey() + "=" + entry.getValue())
            .collect(java.util.stream.Collectors.joining("\n"));
    byte[] secretKey =
        hmac(WEB_APP_DATA.getBytes(StandardCharsets.UTF_8), config.botToken().orElseThrow());
    String expectedHash = hex(hmac(secretKey, dataCheckString));
    return MessageDigest.isEqual(
        expectedHash.getBytes(StandardCharsets.US_ASCII),
        receivedHash.toLowerCase().getBytes(StandardCharsets.US_ASCII));
  }

  private long authDate(Map<String, String> values) {
    return Long.parseLong(values.getOrDefault("auth_date", "-1"));
  }

  private boolean isFresh(long authDate) {
    long age = timeProvider.currentEpochSecond() - authDate;
    return authDate >= 0 && age >= 0 && age <= config.initDataMaxAgeSeconds();
  }

  private JsonNode user(Map<String, String> values) throws Exception {
    JsonNode user = userReader.readTree(values.get("user"));
    return user;
  }

  private long userId(JsonNode user) {
    return user != null && user.has("id") && user.get("id").canConvertToLong()
        ? user.get("id").longValue()
        : -1;
  }

  private String text(JsonNode node, String field) {
    return node != null && node.hasNonNull(field) ? node.get(field).asText() : null;
  }

  private String displayName(JsonNode node) {
    String first = text(node, "first_name");
    String last = text(node, "last_name");
    return first == null ? last : last == null ? first : first + " " + last;
  }

  private Map<String, String> parse(String rawInitData) {
    Map<String, String> values = new TreeMap<>();
    for (String pair : rawInitData.split("&")) {
      String[] parts = pair.split("=", 2);
      if (parts.length != 2 || parts[0].isBlank()) {
        throw new IllegalArgumentException("Invalid Telegram init data");
      }
      String key = decode(parts[0]);
      if (values.putIfAbsent(key, decode(parts[1])) != null) {
        throw new IllegalArgumentException("Duplicate Telegram init data field");
      }
    }
    return values;
  }

  private String decode(String value) {
    return URLDecoder.decode(value, StandardCharsets.UTF_8);
  }

  private byte[] hmac(byte[] key, String value) throws Exception {
    return hmac(key, value.getBytes(StandardCharsets.UTF_8));
  }

  private byte[] hmac(byte[] key, byte[] value) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(key, "HmacSHA256"));
    return mac.doFinal(value);
  }

  private String hex(byte[] value) {
    return java.util.HexFormat.of().formatHex(value);
  }

  public record VerifiedInitData(
      long telegramUserId,
      Instant authenticatedAt,
      String telegramUsername,
      String telegramDisplayName) {
    public VerifiedInitData(long telegramUserId, Instant authenticatedAt) {
      this(telegramUserId, authenticatedAt, null, null);
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof VerifiedInitData value
          && telegramUserId == value.telegramUserId
          && authenticatedAt.equals(value.authenticatedAt);
    }

    @Override
    public int hashCode() {
      return java.util.Objects.hash(telegramUserId, authenticatedAt);
    }
  }
}
