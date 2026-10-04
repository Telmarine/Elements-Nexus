package com.tac5studios.elementsnexus.discord;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tac5studios.elementsnexus.ElementsNexus;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * A small Discord gateway connection: receives messages and member updates.
 * Reconnects by itself if the connection drops.
 */
final class DiscordGateway implements WebSocket.Listener {

    private static final String URL = "wss://gateway.discord.gg/?v=10&encoding=json";

    private final HttpClient http;
    private final String token;
    private final int intents;
    /** (event name, data) - called on the gateway thread. */
    private final BiConsumer<String, JsonObject> onEvent;

    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Nexus-Discord-Gateway");
        t.setDaemon(true);
        return t;
    });
    private final StringBuilder partial = new StringBuilder();
    private volatile WebSocket socket;
    private volatile ScheduledFuture<?> heartbeat;
    private volatile Integer seq;
    private volatile boolean stopped;
    private volatile boolean acked = true;
    private int failures;

    DiscordGateway(HttpClient http, String token, int intents, BiConsumer<String, JsonObject> onEvent) {
        this.http = http;
        this.token = token;
        this.intents = intents;
        this.onEvent = onEvent;
    }

    void start() {
        connect(0);
    }

    void stop() {
        stopped = true;
        if (heartbeat != null) heartbeat.cancel(false);
        WebSocket s = socket;
        if (s != null) s.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
        timer.shutdownNow();
    }

    private void connect(long delaySeconds) {
        if (stopped) return;
        timer.schedule(() -> {
            if (stopped) return;
            retrying = false;
            http.newWebSocketBuilder().buildAsync(URI.create(URL), this).whenComplete((ws, err) -> {
                if (err != null) {
                    ElementsNexus.LOGGER.warn("[Nexus] Discord connection failed: {}", err.toString());
                    retry();
                }
            });
        }, delaySeconds, TimeUnit.SECONDS);
    }

    private volatile boolean retrying;

    private void retry() {
        if (stopped || retrying) return;
        retrying = true;
        if (heartbeat != null) heartbeat.cancel(false);
        failures = Math.min(failures + 1, 6);
        connect(5L * failures); // 5s, 10s ... up to 30s
    }

    // ---------- WebSocket.Listener ----------

    @Override
    public void onOpen(WebSocket ws) {
        socket = ws;
        retrying = false;
        ws.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
        partial.append(data);
        if (last) {
            String msg = partial.toString();
            partial.setLength(0);
            try {
                handle(ws, JsonParser.parseString(msg).getAsJsonObject());
            } catch (Exception e) {
                ElementsNexus.LOGGER.warn("[Nexus] Bad Discord message: {}", e.toString());
            }
        }
        ws.request(1);
        return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket ws, int code, String reason) {
        if (!stopped) {
            if (code == 4004) {
                ElementsNexus.LOGGER.error("[Nexus] Discord rejected the bot token. Check discord.toml.");
                return null;
            }
            if (code == 4014) {
                ElementsNexus.LOGGER.error("[Nexus] Discord: the bot needs \"Message Content Intent\" (and \"Server Members Intent\" for role_to_rank) turned on.");
                return null;
            }
            ElementsNexus.LOGGER.info("[Nexus] Discord connection closed ({}). Reconnecting.", code);
            retry();
        }
        return null;
    }

    @Override
    public void onError(WebSocket ws, Throwable error) {
        if (!stopped) {
            ElementsNexus.LOGGER.warn("[Nexus] Discord connection error: {}", error.toString());
            retry();
        }
    }

    // ---------- gateway protocol ----------

    private void handle(WebSocket ws, JsonObject m) {
        int op = m.get("op").getAsInt();
        if (m.has("s") && !m.get("s").isJsonNull()) seq = m.get("s").getAsInt();
        switch (op) {
            case 10 -> { // hello
                long every = m.getAsJsonObject("d").get("heartbeat_interval").getAsLong();
                acked = true;
                if (heartbeat != null) heartbeat.cancel(false);
                heartbeat = timer.scheduleAtFixedRate(() -> beat(ws), (long) (every * Math.random()), every, TimeUnit.MILLISECONDS);
                identify(ws);
            }
            case 11 -> acked = true;
            case 1 -> beat(ws);
            case 7, 9 -> { // reconnect / invalid session
                ws.sendClose(4000, "reconnect");
            }
            case 0 -> {
                String t = m.get("t").getAsString();
                JsonElement d = m.get("d");
                if ("READY".equals(t)) {
                    failures = 0;
                    ElementsNexus.LOGGER.info("[Nexus] Discord connected.");
                }
                if (d != null && d.isJsonObject()) onEvent.accept(t, d.getAsJsonObject());
            }
            default -> { }
        }
    }

    private void beat(WebSocket ws) {
        if (!acked) { // no reply to the last heartbeat: the connection is dead
            ws.abort();
            retry();
            return;
        }
        acked = false;
        JsonObject b = new JsonObject();
        b.addProperty("op", 1);
        if (seq == null) b.add("d", null);
        else b.addProperty("d", seq);
        ws.sendText(b.toString(), true);
    }

    private void identify(WebSocket ws) {
        JsonObject props = new JsonObject();
        props.addProperty("os", "linux");
        props.addProperty("browser", "elements_nexus");
        props.addProperty("device", "elements_nexus");
        JsonObject d = new JsonObject();
        d.addProperty("token", token);
        d.addProperty("intents", intents);
        d.add("properties", props);
        JsonObject m = new JsonObject();
        m.addProperty("op", 2);
        m.add("d", d);
        ws.sendText(m.toString(), true);
    }
}
