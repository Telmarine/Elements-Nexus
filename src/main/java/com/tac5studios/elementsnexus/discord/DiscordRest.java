package com.tac5studios.elementsnexus.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tac5studios.elementsnexus.ElementsNexus;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Calls to Discord's web API, one at a time on their own thread (keeps us under rate limits). */
final class DiscordRest {

    private static final String API = "https://discord.com/api/v10";

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ExecutorService queue = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Nexus-Discord-Rest");
        t.setDaemon(true);
        return t;
    });
    private final String token;

    DiscordRest(String token) {
        this.token = token;
    }

    HttpClient http() {
        return http;
    }

    /** Post a message. Nobody gets pinged, whatever the text says. */
    void send(String channel, String text) {
        if (channel == null || channel.isBlank() || text == null || text.isBlank()) return;
        String content = clean(text);
        JsonObject body = new JsonObject();
        body.addProperty("content", content.length() > 2000 ? content.substring(0, 1997) + "..." : content);
        JsonObject mentions = new JsonObject();
        mentions.add("parse", new JsonArray());
        body.add("allowed_mentions", mentions);
        queue.submit(() -> call("POST", "/channels/" + channel.trim() + "/messages", body.toString()));
    }

    void addRole(String guild, String user, String role) {
        queue.submit(() -> call("PUT", "/guilds/" + guild + "/members/" + user + "/roles/" + role, null));
    }

    void removeRole(String guild, String user, String role) {
        queue.submit(() -> call("DELETE", "/guilds/" + guild + "/members/" + user + "/roles/" + role, null));
    }

    /** Role IDs a member has, or null. Blocks; call off the server thread. */
    JsonArray memberRoles(String guild, String user) {
        String r = call("GET", "/guilds/" + guild + "/members/" + user, null);
        if (r == null) return null;
        try {
            return JsonParser.parseString(r).getAsJsonObject().getAsJsonArray("roles");
        } catch (Exception e) {
            return null;
        }
    }

    /** Wait for queued messages (used at shutdown so "Server stopped" gets out). */
    void drain(int seconds) {
        queue.shutdown();
        try {
            queue.awaitTermination(seconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Make an API call. Retries once on a rate limit. Returns the body, or null on failure. */
    String call(String method, String path, String json) {
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(API + path))
                        .timeout(Duration.ofSeconds(15))
                        .header("Authorization", "Bot " + token)
                        .header("User-Agent", "DiscordBot (https://github.com/Telmarine, 1.0) ElementsNexus");
                if (json != null) {
                    rb.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(json));
                } else {
                    rb.method(method, HttpRequest.BodyPublishers.noBody());
                }
                HttpResponse<String> res = http.send(rb.build(), HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() == 429) {
                    double wait = 1;
                    try {
                        wait = JsonParser.parseString(res.body()).getAsJsonObject().get("retry_after").getAsDouble();
                    } catch (Exception ignored) {
                    }
                    Thread.sleep((long) (Math.min(wait, 30) * 1000) + 100);
                    continue;
                }
                if (res.statusCode() >= 300) {
                    ElementsNexus.LOGGER.warn("[Nexus] Discord {} {} failed: {} {}", method, path, res.statusCode(), shorten(res.body()));
                    return null;
                }
                return res.body();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (Exception e) {
                ElementsNexus.LOGGER.warn("[Nexus] Discord {} {} failed: {}", method, path, e.toString());
                return null;
            }
        }
        return null;
    }

    /** Break @everyone / @here and role mentions so they show as plain text. */
    static String clean(String text) {
        return text.replace("@everyone", "@​everyone").replace("@here", "@​here")
                .replaceAll("<@&(\\d+)>", "@role");
    }

    private static String shorten(String s) {
        return s == null ? "" : s.length() > 200 ? s.substring(0, 200) : s;
    }
}
