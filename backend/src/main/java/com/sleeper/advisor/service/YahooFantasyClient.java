package com.sleeper.advisor.service;

import com.sleeper.advisor.model.DraftPick;
import com.sleeper.advisor.model.LeagueUser;
import com.sleeper.advisor.model.Player;
import com.sleeper.advisor.model.Roster;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Service
public class YahooFantasyClient {

    private static final String API_BASE = "https://fantasysports.yahooapis.com/fantasy/v2";
    private static final String AUTH_URL = "https://api.login.yahoo.com/oauth2/request_auth";
    private static final String TOKEN_URL = "https://api.login.yahoo.com/oauth2/get_token";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.yahoo.client-id:}")
    private String clientId;

    @Value("${app.yahoo.client-secret:}")
    private String clientSecret;

    @Value("${app.yahoo.redirect-uri:http://localhost:8080/api/yahoo/callback}")
    private String redirectUri;

    @Value("${app.yahoo.refresh-token:}")
    private String configuredRefreshToken;

    private volatile YahooToken token;

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
    }

    public boolean isConnected() {
        return isConfigured() && (token != null || (configuredRefreshToken != null && !configuredRefreshToken.isBlank()));
    }

    public String authUrl() {
        if (!isConfigured()) {
            throw new IllegalStateException("Yahoo OAuth is not configured. Set YAHOO_CLIENT_ID and YAHOO_CLIENT_SECRET.");
        }
        return UriComponentsBuilder.fromUriString(AUTH_URL)
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", "fspt-r")
                .queryParam("language", "en-us")
                .queryParam("state", "fantasy-wizard")
                .build()
                .toUriString();
    }

    public void exchangeCode(String code) {
        if (!isConfigured()) {
            throw new IllegalStateException("Yahoo OAuth is not configured.");
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("redirect_uri", redirectUri);
        form.add("code", code);
        this.token = requestToken(form);
    }

    public List<Map<String, Object>> getUserLeagues(String season) {
        Map<String, Object> root = get("/users;use_login=1/games;game_keys=nfl/teams");
        List<Map<String, Object>> teams = findMapsWithKey(root, "team_key");
        Map<String, Map<String, Object>> leagues = new LinkedHashMap<>();
        for (Map<String, Object> team : teams) {
            String teamKey = stringValue(team.get("team_key"));
            if (teamKey == null || !teamKey.contains(".l.")) continue;
            String leagueKey = leagueKeyFromTeamKey(teamKey);
            String teamName = firstString(team, "name");
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("leagueId", teamKey);
            summary.put("name", teamName != null ? "Yahoo: " + teamName : "Yahoo League " + leagueKey);
            summary.put("provider", "yahoo");
            summary.put("leagueKey", leagueKey);
            leagues.putIfAbsent(teamKey, summary);
        }
        return new ArrayList<>(leagues.values());
    }

    public List<LeagueUser> getLeagueMembers(String yahooLeagueId, String username) {
        String leagueKey = leagueKeyFromAnyKey(yahooLeagueId);
        Map<String, Object> root = get("/league/" + enc(leagueKey) + "/standings");
        List<Map<String, Object>> teams = findMapsWithKey(root, "team_key");
        List<LeagueUser> out = new ArrayList<>();
        for (Map<String, Object> team : teams) {
            String teamKey = stringValue(team.get("team_key"));
            if (teamKey == null || !teamKey.contains(".t.")) continue;
            String name = firstString(team, "name");
            if (name == null) name = "Yahoo Team " + teamKey.substring(teamKey.lastIndexOf(".t.") + 3);
            boolean isMe = yahooLeagueId.equals(teamKey) || (username != null && name.equalsIgnoreCase(username));
            out.add(new LeagueUser(teamKey, name, null, isMe));
        }
        return dedupeUsers(out);
    }

    public Roster getRoster(String yahooLeagueId, String userId, int week) {
        String teamKey = userId != null && userId.contains(".t.") ? userId : yahooLeagueId;
        Map<String, Object> root = get("/team/" + enc(teamKey) + "/roster;week=" + week + "/players");
        List<Map<String, Object>> playerMaps = findMapsWithKey(root, "player_key");
        List<Player> starters = new ArrayList<>();
        List<Player> bench = new ArrayList<>();
        for (Map<String, Object> playerMap : playerMaps) {
            Player player = toPlayer(playerMap);
            if (player == null) continue;
            String slot = selectedPosition(playerMap);
            if ("BN".equalsIgnoreCase(slot) || "IR".equalsIgnoreCase(slot)) {
                bench.add(player);
            } else {
                starters.add(player);
            }
        }
        return new Roster(starters, bench, List.of(), List.<DraftPick>of());
    }

    public List<Map<String, Object>> getAllRosters(String yahooLeagueId, int week) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (LeagueUser user : getLeagueMembers(yahooLeagueId, null)) {
            Roster roster = getRoster(yahooLeagueId, user.userId(), week);
            Map<String, Object> team = new LinkedHashMap<>();
            team.put("displayName", user.displayName());
            team.put("userId", user.userId());
            team.put("starters", roster.starters());
            team.put("bench", roster.bench());
            team.put("taxi", roster.taxi());
            out.add(team);
        }
        return out;
    }

    private YahooToken requestToken(MultiValueMap<String, String> form) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(clientId, clientSecret);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = restTemplate.postForObject(
                    TOKEN_URL,
                    new HttpEntity<>(form, headers),
                    Map.class
            );
            if (body == null || body.get("access_token") == null) {
                throw new IllegalStateException("Yahoo token response did not include an access token.");
            }
            String accessToken = body.get("access_token").toString();
            String refreshToken = body.get("refresh_token") != null
                    ? body.get("refresh_token").toString()
                    : configuredRefreshToken;
            long expiresIn = body.get("expires_in") instanceof Number n ? n.longValue() : 3600;
            return new YahooToken(accessToken, refreshToken, Instant.now().plusSeconds(Math.max(60, expiresIn - 60)));
        } catch (HttpClientErrorException e) {
            throw new IllegalStateException("Yahoo OAuth token exchange failed: " + e.getResponseBodyAsString(), e);
        }
    }

    private String accessToken() {
        if (token == null && configuredRefreshToken != null && !configuredRefreshToken.isBlank()) {
            token = new YahooToken("", configuredRefreshToken, Instant.EPOCH);
        }
        if (token == null) {
            throw new IllegalStateException("Yahoo is not connected. Open /api/yahoo/auth-url and complete OAuth first.");
        }
        if (token.accessToken().isBlank() || Instant.now().isAfter(token.expiresAt())) {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "refresh_token");
            form.add("refresh_token", token.refreshToken());
            token = requestToken(form);
        }
        return token.accessToken();
    }

    private Map<String, Object> get(String path) {
        String url = API_BASE + path + (path.contains("?") ? "&" : "?") + "format=json";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken());
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(response.getBody(), Map.class);
            return parsed;
        } catch (HttpClientErrorException.Unauthorized e) {
            token = token != null ? new YahooToken("", token.refreshToken(), Instant.EPOCH) : null;
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Yahoo API request failed for " + path + ": " + e.getMessage(), e);
        }
    }

    private Player toPlayer(Map<String, Object> playerMap) {
        String id = stringValue(playerMap.get("player_key"));
        if (id == null) return null;
        String name = nestedFullName(playerMap);
        if (name == null) name = firstString(playerMap, "name");
        if (name == null) name = id;
        String pos = firstString(playerMap, "display_position");
        if (pos == null) pos = firstString(playerMap, "primary_position");
        String team = firstString(playerMap, "editorial_team_abbr");
        return new Player(id, name, pos != null ? pos : "", team != null ? team : "", null, null);
    }

    private String selectedPosition(Map<String, Object> playerMap) {
        Object selected = playerMap.get("selected_position");
        if (selected instanceof Map<?, ?> m) {
            Object pos = m.get("position");
            if (pos != null) return pos.toString();
        }
        List<Map<String, Object>> nested = findMapsWithKey(selected, "position");
        for (Map<String, Object> map : nested) {
            String pos = stringValue(map.get("position"));
            if (pos != null) return pos;
        }
        return "";
    }

    private String nestedFullName(Map<String, Object> map) {
        Object name = map.get("name");
        if (name instanceof Map<?, ?> nameMap) {
            Object full = nameMap.get("full");
            if (full != null) return full.toString();
        }
        List<Map<String, Object>> maps = findMapsWithKey(name, "full");
        for (Map<String, Object> m : maps) {
            String full = stringValue(m.get("full"));
            if (full != null) return full;
        }
        return null;
    }

    private static List<LeagueUser> dedupeUsers(List<LeagueUser> users) {
        Map<String, LeagueUser> byId = new LinkedHashMap<>();
        for (LeagueUser user : users) {
            if (user.userId() != null && !user.userId().isBlank()) byId.putIfAbsent(user.userId(), user);
        }
        return new ArrayList<>(byId.values());
    }

    private static String leagueKeyFromAnyKey(String key) {
        if (key == null) return "";
        return key.contains(".t.") ? leagueKeyFromTeamKey(key) : key;
    }

    private static String leagueKeyFromTeamKey(String teamKey) {
        int teamPart = teamKey.indexOf(".t.");
        return teamPart > 0 ? teamKey.substring(0, teamPart) : teamKey;
    }

    private static String firstString(Map<String, Object> map, String key) {
        Object direct = map.get(key);
        String directString = stringValue(direct);
        if (directString != null) return directString;
        List<Map<String, Object>> nested = findMapsWithKey(map, key);
        for (Map<String, Object> nestedMap : nested) {
            String value = stringValue(nestedMap.get(key));
            if (value != null) return value;
        }
        return null;
    }

    private static String stringValue(Object value) {
        if (value == null) return null;
        if (value instanceof String s) return s;
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        return null;
    }

    private static List<Map<String, Object>> findMapsWithKey(Object root, String key) {
        List<Map<String, Object>> out = new ArrayList<>();
        walk(root, key, out);
        return out;
    }

    @SuppressWarnings("unchecked")
    private static void walk(Object node, String key, List<Map<String, Object>> out) {
        if (node instanceof Map<?, ?> map) {
            if (map.containsKey(key)) {
                Map<String, Object> typed = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    typed.put(entry.getKey().toString(), entry.getValue());
                }
                out.add(typed);
            }
            for (Object value : map.values()) walk(value, key, out);
        } else if (node instanceof Iterable<?> iterable) {
            for (Object value : iterable) walk(value, key, out);
        }
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record YahooToken(String accessToken, String refreshToken, Instant expiresAt) {}
}
