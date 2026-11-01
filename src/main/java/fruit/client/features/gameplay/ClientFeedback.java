package fruit.client.features.gameplay;

import static fruit.common.MessageData.player;
import static fruit.common.Protocol.integer;
import static fruit.common.Protocol.number;
import static fruit.common.Protocol.obj;
import static fruit.common.Protocol.str;

import fruit.client.application.ClientState;
import fruit.client.features.settings.ClientAudio;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Deduplicates server events, schedules audio and owns transient visual feedback. */
public final class ClientFeedback {
    public final ClientAudio audio = new ClientAudio();
    public final GameplayNotice notices = new GameplayNotice();
    public final List<CatchEffect> effects = new ArrayList<>();
    public final Set<String> caughtEvents = new HashSet<>();
    public Map<String, Object> previousState = obj();
    public long stateReceived;
    public int lastCountdown = -1;
    public int lastSecond = 61;
    public int catchVariant;
    public String presentationMatch = "";
    public String resultSoundMatch = "";
    public boolean restoringMatch;
    public long flashUntil;
    public String effect = "";
    public boolean effectOnMe;
    public long targetPulseStart;
    private final ClientState model;

    public ClientFeedback(ClientState model) {
        this.model = model;
    }

    public void catchFeedback(Map<String, Object> event) {
        if (!Objects.equals(event.get("matchId"), model.state.get("matchId"))) return;
        String key =
                event.get("matchId") + ":" + event.get("playerId") + ":" + event.get("objectId");
        if (!caughtEvents.add(key)) return;
        if (caughtEvents.size() > 1024) {
            caughtEvents.clear();
            caughtEvents.add(key);
        }
        int id = integer(event, "playerId"), delta = integer(event, "delta");
        addEffect(
                new CatchEffect(
                        id, number(event, "x"), number(event, "y"), str(event, "kind"), delta));
        boolean own = id == integer(model.user, "id");
        audio.play(
                delta > 0
                        ? "catch-" + (1 + (catchVariant++ % 3))
                        : delta == -10 ? "bomb-hit" : "wrong",
                own ? 1 : .3);
        if (delta > 10) audio.play("combo", own ? .9 : .25);
        if (delta == -10) audio.duckMusic();
    }

    public void receiveState(Map<String, Object> event) {
        Map<String, Object> old = player(model.state, integer(model.user, "id")),
                next = player(event, integer(model.user, "id"));
        if (!Objects.equals(model.state.get("matchId"), event.get("matchId")))
            previousState = obj();
        else previousState = model.state;
        if (number(old, "freeze") > 0 && number(next, "freeze") <= 0) audio.play("freeze-end", .7);
        if (number(old, "magnet") > 0 && number(next, "magnet") <= 0) audio.play("magnet-end", .65);
        model.state = event;
        stateReceived = System.nanoTime();
        if (restoringMatch) {
            lastSecond = (int) Math.ceil(number(event, "remaining"));
            lastCountdown = (int) Math.ceil(number(event, "countdown"));
            restoringMatch = false;
            return;
        }
        if (str(event, "phase").equals("COUNTDOWN")) {
            int count = (int) Math.ceil(number(event, "countdown"));
            if (count > 0 && count != lastCountdown) {
                audio.play("countdown", .7);
                lastCountdown = count;
            }
        } else {
            int second = (int) Math.ceil(number(event, "remaining"));
            if (lastSecond > 10 && second <= 10) audio.play("time-warning", .8);
            else if (second > 0 && second <= 5 && second != lastSecond) audio.play("countdown", .5);
            lastSecond = second;
        }
    }

    public void addEffect(CatchEffect fx) {
        if (effects.size() >= 48) effects.remove(0);
        effects.add(fx);
    }
}
