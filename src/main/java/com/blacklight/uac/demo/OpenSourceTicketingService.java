package com.blacklight.uac.demo;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Open-source ticketing bridge for UAC.
 *
 * Provider modes:
 * - local: in-memory ticket board (always available)
 * - gitlab: GitLab CE/EE Issues API (if baseUrl/projectId/token are configured)
 */
public class OpenSourceTicketingService {

    private final HttpClient httpClient;
    private final Map<String, Map<String, Object>> localTickets;

    public OpenSourceTicketingService() {
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
        this.localTickets = new ConcurrentHashMap<>();
    }

    public Map<String, Object> openTicket(TicketConfig config, TicketPayload payload) {
        if (config != null && config.isOpenProjectEnabled()) {
            Map<String, Object> remote = createOpenProjectWorkPackage(config, payload);
            if (!remote.isEmpty()) {
                return remote;
            }
        }
        if (config != null && config.isGitLabEnabled()) {
            Map<String, Object> remote = createGitLabIssue(config, payload);
            if (!remote.isEmpty()) {
                return remote;
            }
        }
        return createLocalTicket(payload);
    }

    public Map<String, Object> updateTicket(
            TicketConfig config,
            Map<String, Object> existingTicket,
            String status,
            String comment,
            List<String> labels
    ) {
        if (existingTicket == null || existingTicket.isEmpty()) {
            return Collections.emptyMap();
        }

        String provider = String.valueOf(existingTicket.getOrDefault("provider", "local")).toLowerCase(Locale.ROOT);
        if ("openproject".equals(provider) && config != null && config.isOpenProjectEnabled()) {
            return updateOpenProjectWorkPackage(config, existingTicket, status, comment, labels);
        }
        if ("gitlab".equals(provider) && config != null && config.isGitLabEnabled()) {
            return updateGitLabIssue(config, existingTicket, status, comment, labels);
        }
        return updateLocalTicket(existingTicket, status, comment, labels);
    }

    public List<Map<String, Object>> listOpenTickets(TicketConfig config) {
        return listOpenTickets(config, 1, 30);
    }

    public List<Map<String, Object>> listOpenTickets(TicketConfig config, int maxPages, int pageSize) {
        if (config == null || !config.enabled) {
            return Collections.emptyList();
        }
        int boundedPages = Math.max(1, Math.min(maxPages, 20));
        int boundedPageSize = Math.max(10, Math.min(pageSize, 100));

        // Honor explicit provider selection; do not silently downgrade to local for polling.
        String provider = config.provider == null ? "" : config.provider.trim().toLowerCase(Locale.ROOT);
        if ("openproject".equals(provider)) {
            if (!config.isOpenProjectEnabled()) {
                return Collections.emptyList();
            }
            return listOpenProjectWorkPackages(config, boundedPages, boundedPageSize);
        }
        if ("gitlab".equals(provider)) {
            if (!config.isGitLabEnabled()) {
                return Collections.emptyList();
            }
            return listGitLabIssues(config, boundedPages, boundedPageSize);
        }

        if (config.isOpenProjectEnabled()) {
            return listOpenProjectWorkPackages(config, boundedPages, boundedPageSize);
        }
        if (config.isGitLabEnabled()) {
            return listGitLabIssues(config, boundedPages, boundedPageSize);
        }
        return listLocalTickets();
    }

    private List<Map<String, Object>> listLocalTickets() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> ticket : localTickets.values()) {
            String status = String.valueOf(ticket.getOrDefault("status", "OPEN"));
            if (isFinalStatus(status) || "CLOSED".equalsIgnoreCase(status)) {
                continue;
            }
            out.add(new HashMap<>(ticket));
        }
        return out;
    }

    private List<Map<String, Object>> listOpenProjectWorkPackages(TicketConfig config, int maxPages, int pageSize) {
        try {
            String base = normalizeBaseUrl(config.baseUrl);
            String project = urlEncode(config.projectId);
            Pattern wpPattern = Pattern.compile(
                    "\\\"subject\\\"\\s*:\\s*\\\"([^\\\"]*)\\\".*?"
                            + "\\\"self\\\"\\s*:\\s*\\{\\s*\\\"href\\\"\\s*:\\s*\\\"/api/v3/work_packages/(\\d+)\\\"",
                    Pattern.DOTALL
            );

            Map<Integer, Map<String, Object>> dedup = new java.util.LinkedHashMap<>();
            for (int page = 0; page < maxPages; page++) {
                int offset = page * pageSize;
                String url = base + "/api/v3/projects/" + project + "/work_packages?pageSize=" + pageSize + "&offset=" + offset;

                HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(8))
                        .header("Authorization", openProjectAuthHeader(config.token))
                        .header("Accept", "application/json")
                        .GET()
                        .build();

                HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() < 200 || res.statusCode() >= 300) {
                    break;
                }

                String body = res.body() == null ? "" : res.body();
                Matcher matcher = wpPattern.matcher(body);
                int matched = 0;
                while (matcher.find()) {
                    matched++;
                    String title = matcher.group(1);
                    Integer id = Integer.parseInt(matcher.group(2));

                    Map<String, Object> ticket = new HashMap<>();
                    ticket.put("provider", "openproject");
                    ticket.put("id", id);
                    ticket.put("key", "#" + id);
                    ticket.put("title", defaultIfBlank(title, "OpenProject work package #" + id));
                    ticket.put("status", "OPEN");
                    ticket.put("url", base + "/work_packages/" + id);
                    ticket.put("labels", new ArrayList<String>());
                    dedup.putIfAbsent(id, ticket);
                }

                if (matched == 0) {
                    break;
                }
            }
            return new ArrayList<>(dedup.values());
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    private List<Map<String, Object>> listGitLabIssues(TicketConfig config, int maxPages, int pageSize) {
        try {
            String base = normalizeBaseUrl(config.baseUrl);
            String project = URLEncoder.encode(config.projectId, StandardCharsets.UTF_8);
            List<Map<String, Object>> tickets = new ArrayList<>();
            for (int page = 1; page <= maxPages; page++) {
                String url = base + "/api/v4/projects/" + project + "/issues?state=opened&per_page=" + pageSize + "&page=" + page;

                HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(8))
                        .header("PRIVATE-TOKEN", config.token)
                        .header("Accept", "application/json")
                        .GET()
                        .build();

                HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() < 200 || res.statusCode() >= 300) {
                    break;
                }

                String body = res.body() == null ? "" : res.body();
                List<String> iids = extractMatches(body, Pattern.compile("\\\"iid\\\"\\s*:\\s*(\\d+)"));
                List<String> titles = extractMatches(body, Pattern.compile("\\\"title\\\"\\s*:\\s*\\\"([^\\\"]*)\\\""));
                List<String> urls = extractMatches(body, Pattern.compile("\\\"web_url\\\"\\s*:\\s*\\\"([^\\\"]*)\\\""));
                if (iids.isEmpty()) {
                    break;
                }

                for (int i = 0; i < iids.size(); i++) {
                    String iid = iids.get(i);
                    String title = i < titles.size() ? titles.get(i) : "GitLab issue #" + iid;
                    String webUrl = i < urls.size() ? urls.get(i) : "";

                    Map<String, Object> ticket = new HashMap<>();
                    ticket.put("provider", "gitlab");
                    ticket.put("id", Integer.parseInt(iid));
                    ticket.put("key", "GL-" + iid);
                    ticket.put("title", defaultIfBlank(title, "GitLab issue #" + iid));
                    ticket.put("status", "OPEN");
                    ticket.put("url", defaultIfBlank(webUrl, ""));
                    ticket.put("labels", new ArrayList<String>());
                    tickets.add(ticket);
                }
            }
            return tickets;
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    private List<String> extractMatches(String input, Pattern pattern) {
        if (input == null || input.isBlank()) {
            return List.of();
        }
        List<String> matches = new ArrayList<>();
        Matcher matcher = pattern.matcher(input);
        while (matcher.find()) {
            matches.add(matcher.group(1));
        }
        return matches;
    }

    private Map<String, Object> createOpenProjectWorkPackage(TicketConfig config, TicketPayload payload) {
        try {
            String base = normalizeBaseUrl(config.baseUrl);
            String project = urlEncode(config.projectId);
            String url = base + "/api/v3/projects/" + project + "/work_packages";

            String body = "{"
                    + "\"subject\":" + toJsonString(payload.title) + ","
                    + "\"description\":{\"format\":\"markdown\",\"raw\":" + toJsonString(payload.description) + "},"
                    + "\"_links\":{\"type\":{\"href\":\"/api/v3/types/" + config.openProjectTypeId + "\"}}"
                    + "}";

            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .header("Authorization", openProjectAuthHeader(config.token))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() < 200 || res.statusCode() >= 300) {
                return Collections.emptyMap();
            }

            String response = res.body();
            Integer id = extractOpenProjectWorkPackageId(response);
            if (id == null) {
                id = extractInt(response, "id");
            }
            Integer lockVersion = extractInt(response, "lockVersion");
            String subject = extractString(response, "subject");
            if (id == null) {
                return Collections.emptyMap();
            }

            Map<String, Object> ticket = new HashMap<>();
            ticket.put("provider", "openproject");
            ticket.put("id", id);
            ticket.put("key", "#" + id);
            ticket.put("url", base + "/work_packages/" + id);
            ticket.put("title", subject == null ? payload.title : subject);
            ticket.put("status", "OPEN");
            ticket.put("labels", new ArrayList<>(payload.labels));
            ticket.put("comments", new ArrayList<>(List.of("Ticket created by UAC")));
            ticket.put("lockVersion", lockVersion == null ? 0 : lockVersion);
            return ticket;
        } catch (IOException | InterruptedException ignored) {
            return Collections.emptyMap();
        }
    }

    private Map<String, Object> updateOpenProjectWorkPackage(
            TicketConfig config,
            Map<String, Object> ticket,
            String status,
            String comment,
            List<String> labels
    ) {
        try {
            String base = normalizeBaseUrl(config.baseUrl);
            String id = String.valueOf(ticket.getOrDefault("id", ""));
            if (id.isBlank()) {
                return updateLocalTicket(ticket, status, comment, labels);
            }

            HttpRequest getReq = HttpRequest.newBuilder(URI.create(base + "/api/v3/work_packages/" + id))
                    .timeout(Duration.ofSeconds(8))
                    .header("Authorization", openProjectAuthHeader(config.token))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> getRes = httpClient.send(getReq, HttpResponse.BodyHandlers.ofString());
            if (getRes.statusCode() < 200 || getRes.statusCode() >= 300) {
                return updateLocalTicket(ticket, status, comment, labels);
            }

            String current = getRes.body();
            Integer lockVersion = extractInt(current, "lockVersion");
            String description = extractNestedRawDescription(current);

            String statusLine = "[UAC] workflow status: " + defaultIfBlank(status, "OPEN");
            String labelsLine = labels == null || labels.isEmpty() ? "" : " | labels=" + String.join(",", labels);
            String commentLine = comment == null || comment.isBlank() ? "" : " | note=" + comment;
            String appended = defaultIfBlank(description, "") + "\n\n- " + statusLine + labelsLine + commentLine;

            String patchBody = "{"
                    + "\"lockVersion\":" + (lockVersion == null ? 0 : lockVersion) + ","
                    + "\"description\":{\"format\":\"markdown\",\"raw\":" + toJsonString(appended) + "}"
                    + "}";

            HttpRequest patchReq = HttpRequest.newBuilder(URI.create(base + "/api/v3/work_packages/" + id))
                    .timeout(Duration.ofSeconds(8))
                    .header("Authorization", openProjectAuthHeader(config.token))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(patchBody))
                    .build();
            HttpResponse<String> patchRes = httpClient.send(patchReq, HttpResponse.BodyHandlers.ofString());
            if (patchRes.statusCode() < 200 || patchRes.statusCode() >= 300) {
                return updateLocalTicket(ticket, status, comment, labels);
            }

            List<String> mergedLabels = mergeLabels(ticket, labels, status);
            Map<String, Object> updated = new HashMap<>(ticket);
            updated.put("status", status);
            updated.put("labels", mergedLabels);
            Integer latestLockVersion = extractInt(patchRes.body(), "lockVersion");
            if (latestLockVersion != null) {
                updated.put("lockVersion", latestLockVersion);
            }

            Object commentsObj = updated.get("comments");
            List<String> comments = commentsObj instanceof List<?> existing
                    ? new ArrayList<>(existing.stream().map(String::valueOf).toList())
                    : new ArrayList<>();
            if (comment != null && !comment.isBlank()) {
                comments.add(comment);
            }
            updated.put("comments", comments);
            return updated;
        } catch (Exception ignored) {
            return updateLocalTicket(ticket, status, comment, labels);
        }
    }

    private Map<String, Object> createLocalTicket(TicketPayload payload) {
        String key = "UAC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        Map<String, Object> ticket = new HashMap<>();
        ticket.put("provider", "local");
        ticket.put("id", key);
        ticket.put("key", key);
        ticket.put("url", "local://tickets/" + key);
        ticket.put("title", payload.title);
        ticket.put("status", "OPEN");
        ticket.put("labels", new ArrayList<>(payload.labels));
        List<String> comments = new ArrayList<>();
        comments.add("Ticket created by UAC");
        comments.add(payload.description);
        ticket.put("comments", comments);
        localTickets.put(key, ticket);
        return new HashMap<>(ticket);
    }

    private Map<String, Object> updateLocalTicket(Map<String, Object> existingTicket, String status, String comment, List<String> labels) {
        String key = String.valueOf(existingTicket.getOrDefault("key", existingTicket.get("id")));
        Map<String, Object> ticket = localTickets.getOrDefault(key, new HashMap<>(existingTicket));
        if (status != null && !status.isBlank()) {
            ticket.put("status", status);
        }

        LinkedHashSet<String> mergedLabels = new LinkedHashSet<>();
        Object existingLabels = ticket.get("labels");
        if (existingLabels instanceof List<?> labelList) {
            for (Object label : labelList) {
                mergedLabels.add(String.valueOf(label));
            }
        }
        if (labels != null) {
            mergedLabels.addAll(labels);
        }
        ticket.put("labels", new ArrayList<>(mergedLabels));

        Object commentsObj = ticket.get("comments");
        List<String> comments;
        if (commentsObj instanceof List<?>) {
            comments = new ArrayList<>();
            for (Object c : (List<?>) commentsObj) {
                comments.add(String.valueOf(c));
            }
        } else {
            comments = new ArrayList<>();
        }
        if (comment != null && !comment.isBlank()) {
            comments.add(comment);
        }
        ticket.put("comments", comments);

        localTickets.put(key, ticket);
        return new HashMap<>(ticket);
    }

    private Map<String, Object> createGitLabIssue(TicketConfig config, TicketPayload payload) {
        try {
            String base = normalizeBaseUrl(config.baseUrl);
            String project = URLEncoder.encode(config.projectId, StandardCharsets.UTF_8);
            String url = base + "/api/v4/projects/" + project + "/issues";

            String body = "title=" + urlEncode(payload.title)
                    + "&description=" + urlEncode(payload.description)
                    + "&labels=" + urlEncode(String.join(",", payload.labels));

            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(6))
                    .header("PRIVATE-TOKEN", config.token)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() < 200 || res.statusCode() >= 300) {
                return Collections.emptyMap();
            }

            String response = res.body();
            Integer iid = extractInt(response, "iid");
            String webUrl = extractString(response, "web_url");
            String title = extractString(response, "title");
            if (iid == null) {
                return Collections.emptyMap();
            }

            Map<String, Object> ticket = new HashMap<>();
            ticket.put("provider", "gitlab");
            ticket.put("id", iid);
            ticket.put("key", "GL-" + iid);
            ticket.put("url", webUrl == null ? "" : webUrl);
            ticket.put("title", title == null ? payload.title : title);
            ticket.put("status", "OPEN");
            ticket.put("labels", new ArrayList<>(payload.labels));
            ticket.put("comments", new ArrayList<>(List.of("Ticket created by UAC")));
            return ticket;
        } catch (IOException | InterruptedException ignored) {
            return Collections.emptyMap();
        }
    }

    private Map<String, Object> updateGitLabIssue(
            TicketConfig config,
            Map<String, Object> ticket,
            String status,
            String comment,
            List<String> labels
    ) {
        try {
            String base = normalizeBaseUrl(config.baseUrl);
            String project = URLEncoder.encode(config.projectId, StandardCharsets.UTF_8);
            String issueId = String.valueOf(ticket.getOrDefault("id", ""));
            if (issueId.isBlank()) {
                return updateLocalTicket(ticket, status, comment, labels);
            }

            List<String> mergedLabels = mergeLabels(ticket, labels, status);
            String issueUrl = base + "/api/v4/projects/" + project + "/issues/" + issueId;
            String stateEvent = isFinalStatus(status) ? "close" : "reopen";
            String issueBody = "labels=" + urlEncode(String.join(",", mergedLabels))
                    + "&state_event=" + urlEncode(stateEvent);

            HttpRequest updateIssue = HttpRequest.newBuilder(URI.create(issueUrl))
                    .timeout(Duration.ofSeconds(6))
                    .header("PRIVATE-TOKEN", config.token)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .PUT(HttpRequest.BodyPublishers.ofString(issueBody))
                    .build();
            httpClient.send(updateIssue, HttpResponse.BodyHandlers.ofString());

            if (comment != null && !comment.isBlank()) {
                String noteUrl = issueUrl + "/notes";
                HttpRequest noteReq = HttpRequest.newBuilder(URI.create(noteUrl))
                        .timeout(Duration.ofSeconds(6))
                        .header("PRIVATE-TOKEN", config.token)
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString("body=" + urlEncode(comment)))
                        .build();
                httpClient.send(noteReq, HttpResponse.BodyHandlers.ofString());
            }

            Map<String, Object> updated = new HashMap<>(ticket);
            updated.put("status", status);
            updated.put("labels", mergedLabels);
            Object commentsObj = updated.get("comments");
            List<String> comments = commentsObj instanceof List<?> existing
                    ? new ArrayList<>(existing.stream().map(String::valueOf).toList())
                    : new ArrayList<>();
            if (comment != null && !comment.isBlank()) {
                comments.add(comment);
            }
            updated.put("comments", comments);
            return updated;
        } catch (Exception ignored) {
            return updateLocalTicket(ticket, status, comment, labels);
        }
    }

    private List<String> mergeLabels(Map<String, Object> ticket, List<String> incoming, String status) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        Object existing = ticket.get("labels");
        if (existing instanceof List<?> labels) {
            for (Object label : labels) {
                String l = String.valueOf(label);
                if (!l.startsWith("status::")) {
                    merged.add(l);
                }
            }
        }
        if (incoming != null) {
            merged.addAll(incoming);
        }
        if (status != null && !status.isBlank()) {
            merged.add("status::" + status.toLowerCase(Locale.ROOT));
        }
        return new ArrayList<>(merged);
    }

    private boolean isFinalStatus(String status) {
        if (status == null) return false;
        return "COMPLETED".equalsIgnoreCase(status) || "DEPLOYED".equalsIgnoreCase(status);
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String toJsonString(String value) {
        String safe = value == null ? "" : value;
        safe = safe
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
        return "\"" + safe + "\"";
    }

    private String openProjectAuthHeader(String token) {
        String pair = "apikey:" + (token == null ? "" : token);
        return "Basic " + Base64.getEncoder().encodeToString(pair.getBytes(StandardCharsets.UTF_8));
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "";
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private Integer extractInt(String json, String key) {
        Pattern p = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*(\\d+)");
        Matcher m = p.matcher(json == null ? "" : json);
        if (!m.find()) {
            return null;
        }
        return Integer.parseInt(m.group(1));
    }

    private String extractString(String json, String key) {
        Pattern p = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"");
        Matcher m = p.matcher(json == null ? "" : json);
        if (!m.find()) {
            return null;
        }
        return m.group(1);
    }

    private String extractNestedRawDescription(String json) {
        if (json == null || json.isBlank()) {
            return "";
        }
        Pattern p = Pattern.compile("\\\"description\\\"\\s*:\\s*\\{[^{}]*\\\"raw\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"", Pattern.DOTALL);
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1).replace("\\n", "\n") : "";
    }

    private List<String> extractStringArray(String json, String key) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        Pattern p = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL);
        Matcher m = p.matcher(json);
        if (!m.find()) {
            return List.of();
        }
        String raw = m.group(1);
        Matcher itemMatcher = Pattern.compile("\\\"([^\\\"]*)\\\"").matcher(raw);
        List<String> out = new ArrayList<>();
        while (itemMatcher.find()) {
            out.add(itemMatcher.group(1));
        }
        return out;
    }

    private Integer extractOpenProjectWorkPackageId(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        // Prefer explicit work package link because generic "id" can resolve to nested objects.
        Pattern p = Pattern.compile("/api/v3/work_packages/(\\d+)");
        Matcher m = p.matcher(json);
        if (!m.find()) {
            return null;
        }
        return Integer.parseInt(m.group(1));
    }

    public static class TicketConfig {
        public boolean enabled = true;
        public String provider = "local";
        public String baseUrl;
        public String projectId;
        public String token;
        public int openProjectTypeId = 1;

        public boolean isGitLabEnabled() {
            return enabled
                    && provider != null
                    && provider.equalsIgnoreCase("gitlab")
                    && baseUrl != null
                    && !baseUrl.isBlank()
                    && projectId != null
                    && !projectId.isBlank()
                    && token != null
                    && !token.isBlank();
        }

        public boolean isOpenProjectEnabled() {
            return enabled
                    && provider != null
                    && provider.equalsIgnoreCase("openproject")
                    && baseUrl != null
                    && !baseUrl.isBlank()
                    && projectId != null
                    && !projectId.isBlank()
                    && token != null
                    && !token.isBlank();
        }
    }

    public static class TicketPayload {
        public String title;
        public String description;
        public List<String> labels = new ArrayList<>();
    }
}

