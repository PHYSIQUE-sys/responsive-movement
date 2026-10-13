package com.responsivemovement;

import java.io.IOException;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import net.runelite.api.Client;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.util.Filepath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Opt-in immutable snapshots; ordered formatting/file I/O stay off the client thread. */
final class MovementTrace
{
    private static final Logger log = LoggerFactory.getLogger(MovementTrace.class);
    private static CompletableFuture<Void> writer = CompletableFuture.completedFuture(null);
    private static int queuedBatches;
    private static final int ARCHIVES = 7;
    private static final long MAX_FILE_BYTES = 8L * 1024 * 1024;
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault());
    private final List<Entry> pending = new ArrayList<>();
    private final LongSupplier nanoClock;
    private final LongSupplier wallClock;
    private final Predicate<List<Entry>> sink;
    private MovementPath.TraceSnapshot lastRoute;
    private long routeSequence = -1;
    private long clickSequence = -1;
    private int pendingContexts;
    private boolean recordedPositionInBatch;
    private boolean enabled;
    private boolean recording;
    private long started;
    private long session;
    private int lastCycle = -1;
    private long count;
    private long lastFlush;

    @FunctionalInterface
    interface Directory
    {
        Filepath get() throws IOException;
    }

    MovementTrace(Directory directory)
    {
        this(System::nanoTime, System::currentTimeMillis, batch -> submit(batch, directory));
    }

    /** API-double controllers have no filesystem capability; startup supplies it explicitly. */
    MovementTrace(LongSupplier nanoClock) { this(nanoClock, System::currentTimeMillis, batch -> false); }

    MovementTrace(LongSupplier nanoClock, LongSupplier wallClock, Predicate<List<Entry>> sink)
    {
        this.nanoClock = nanoClock; this.wallClock = wallClock; this.sink = sink;
    }

    void enabled(boolean value)
    {
        if (value == enabled) { return; }
        enabled = value;
        if (!value) { close(); return; }
        started = nanoClock.getAsLong(); session = wallClock.getAsLong(); lastFlush = started;
        count = 0; lastCycle = -1; recording = true;
        lastRoute = null; routeSequence = clickSequence = -1;
    }

    void click(Client client, ResponsiveMovementConfig config, int scene, String action,
        MenuOptionClicked event, LocalPoint visible, LocalPoint previousDestination)
    {
        enabled(config.recordTrace());
        if (!recording) { return; }
        clickSequence = count++;
        pending.add(new MovementTraceContext(session, clickSequence, (nanoClock.getAsLong() - started) / 1000,
            client, config, scene, action, event, visible, previousDestination));
        if (++pendingContexts >= 8 || pending.size() >= 128) { flush(); }
    }

    long bookmark(Client client, ResponsiveMovementConfig config, int scene, String source,
        long observedNanos, LocalPoint visible, MovementPath path)
    {
        enabled(config.recordTrace());
        if (!recording) { return -1; }
        long sequence = count++;
        pending.add(new TraceBookmark(session, sequence, Math.max(0, observedNanos - started) / 1000,
            started, clickSequence, routeSequence, client, config, scene, source, visible, path));
        // Use the same ordered asynchronous writer. This is a diagnostic entry,
        // not a position sample or click; neither sampling nor input ownership changes.
        flush();
        return sequence;
    }

    void record(int cycle, int scene, LocalPoint visible, LocalPoint authority, LocalPoint nativePoint,
        MovementPath path, int orientation, int pose, int frame, int action, boolean nativeRender,
        LocalPoint destination, int clickCycle, String startDecision, boolean interactionApproach, String clickAction, int clickTarget,
        boolean canContinue, boolean inputPending, long inputAgeMicros, boolean spot)
    {
        record(cycle, scene, visible, authority, nativePoint, path, orientation, pose, frame, action, nativeRender,
            destination, clickCycle, startDecision, interactionApproach, clickAction, clickTarget,
            canContinue, inputPending, inputAgeMicros, spot, null, null);
    }

    void record(int cycle, int scene, LocalPoint visible, LocalPoint authority, LocalPoint nativePoint,
        MovementPath path, int orientation, int pose, int frame, int action, boolean nativeRender,
        LocalPoint destination, int clickCycle, String startDecision, boolean interactionApproach, String clickAction, int clickTarget,
        boolean canContinue, boolean inputPending, long inputAgeMicros, boolean spot,
        ObjectApproach object, LocalPoint approachGoal)
    {
        record(cycle, scene, visible, authority, nativePoint, path, orientation, pose, frame, action, nativeRender,
            destination, clickCycle, startDecision, interactionApproach, clickAction, clickTarget,
            canContinue, inputPending, inputAgeMicros, spot, object, approachGoal, null);
    }

    void record(int cycle, int scene, LocalPoint visible, LocalPoint authority, LocalPoint nativePoint,
        MovementPath path, int orientation, int pose, int frame, int action, boolean nativeRender,
        LocalPoint destination, int clickCycle, String startDecision, boolean interactionApproach, String clickAction, int clickTarget,
        boolean canContinue, boolean inputPending, long inputAgeMicros, boolean spot,
        ObjectApproach object, LocalPoint approachGoal, NpcApproach npc)
    {
        record(cycle, scene, visible, authority, nativePoint, path, orientation, pose, frame, action, nativeRender,
            destination, clickCycle, startDecision, interactionApproach, clickAction, clickTarget, canContinue,
            inputPending, inputAgeMicros, spot, object, approachGoal, npc, "none", false);
    }

    void record(int cycle, int scene, LocalPoint visible, LocalPoint authority, LocalPoint nativePoint,
        MovementPath path, int orientation, int pose, int frame, int action, boolean nativeRender,
        LocalPoint destination, int clickCycle, String startDecision, boolean interactionApproach, String clickAction, int clickTarget,
        boolean canContinue, boolean inputPending, long inputAgeMicros, boolean spot,
        ObjectApproach object, LocalPoint approachGoal, NpcApproach npc, String combatPhase, boolean combatEngaged)
    {
        record(cycle, scene, visible, authority, nativePoint, path, orientation, pose, frame, action, nativeRender,
            destination, clickCycle, startDecision, interactionApproach, clickAction, clickTarget, canContinue,
            inputPending, inputAgeMicros, spot, object, approachGoal, npc, combatPhase, combatEngaged, false, null, false);
    }

    void record(int cycle, int scene, LocalPoint visible, LocalPoint authority, LocalPoint nativePoint,
        MovementPath path, int orientation, int pose, int frame, int action, boolean nativeRender,
        LocalPoint destination, int clickCycle, String startDecision, boolean interactionApproach, String clickAction, int clickTarget,
        boolean canContinue, boolean inputPending, long inputAgeMicros, boolean spot,
        ObjectApproach object, LocalPoint approachGoal, NpcApproach npc, String combatPhase, boolean combatEngaged,
        boolean combatLocked, LocalPoint combatFaceTarget, boolean combatHit)
    {
        record(cycle, scene, visible, authority, nativePoint, path, orientation, pose, frame, action, nativeRender,
            destination, clickCycle, startDecision, interactionApproach, clickAction, clickTarget, canContinue,
            inputPending, inputAgeMicros, spot, object, approachGoal, npc, combatPhase, combatEngaged,
            combatLocked, combatFaceTarget, combatHit, false);
    }

    void record(int cycle, int scene, LocalPoint visible, LocalPoint authority, LocalPoint nativePoint,
        MovementPath path, int orientation, int pose, int frame, int action, boolean nativeRender,
        LocalPoint destination, int clickCycle, String startDecision, boolean interactionApproach, String clickAction, int clickTarget,
        boolean canContinue, boolean inputPending, long inputAgeMicros, boolean spot,
        ObjectApproach object, LocalPoint approachGoal, NpcApproach npc, String combatPhase, boolean combatEngaged,
        boolean combatLocked, LocalPoint combatFaceTarget, boolean combatHit, boolean combatWalk)
    {
        record(cycle, scene, visible, authority, nativePoint, path, orientation, pose, frame, action, nativeRender,
            destination, clickCycle, startDecision, interactionApproach, clickAction, clickTarget, canContinue,
            inputPending, inputAgeMicros, spot, object, approachGoal, npc, combatPhase, combatEngaged,
            combatLocked, combatFaceTarget, combatHit, combatWalk, false, -1);
    }

    void record(int cycle, int scene, LocalPoint visible, LocalPoint authority, LocalPoint nativePoint,
        MovementPath path, int orientation, int pose, int frame, int action, boolean nativeRender,
        LocalPoint destination, int clickCycle, String startDecision, boolean interactionApproach, String clickAction, int clickTarget,
        boolean canContinue, boolean inputPending, long inputAgeMicros, boolean spot,
        ObjectApproach object, LocalPoint approachGoal, NpcApproach npc, String combatPhase, boolean combatEngaged,
        boolean combatLocked, LocalPoint combatFaceTarget, boolean combatHit, boolean combatWalk, boolean combatEffectCarry, int actionFrame)
    {
        if (!recording) { return; }
        long elapsed = nanoClock.getAsLong() - started;
        if (cycle == lastCycle) { return; }
        lastCycle = cycle;
        MovementPath.TraceSnapshot route = path == null ? null : path.traceSnapshot();
        boolean publishRoute = route != null && (!recordedPositionInBatch || !route.same(lastRoute));
        if (route == null) { routeSequence = -1; }
        else if (publishRoute) { routeSequence = count; }
        lastRoute = route;
        recordedPositionInBatch = true;
        pending.add(new Sample(session, count++, elapsed / 1000, cycle, scene,
            visible, authority, nativePoint, path == null ? "native" : path.phase(),
            path == null ? 0 : path.queuedLegs(), path != null && path.moving(),
            path != null && path.running(), orientation, pose, frame, action, nativeRender,
            destination, clickCycle, startDecision, interactionApproach, clickAction, clickTarget,
            canContinue, inputPending, inputAgeMicros, spot, object, approachGoal, npc,
            clickSequence, routeSequence, publishRoute ? route : null, started, combatPhase, combatEngaged,
            combatLocked, combatFaceTarget, combatHit, path != null && path.combatTrailing(), combatWalk, combatEffectCarry, actionFrame));
        if (pending.size() >= 128) { flush(); }
    }

    void close()
    {
        enabled = false;
        recording = false;
        flush();
        lastRoute = null;
    }

    void poll()
    {
        if (recording && nanoClock.getAsLong() - lastFlush >= 1_000_000_000L) { flush(); }
    }

    /** Avoid extra model/spot scans when no sample would be accepted. */
    boolean recording() { return recording; }
    boolean willSample(int cycle) { return recording && cycle != lastCycle; }

    private void flush()
    {
        if (pending.isEmpty()) { return; }
        List<Entry> batch = List.copyOf(pending);
        pending.clear();
        pendingContexts = 0;
        recordedPositionInBatch = false;
        lastFlush = nanoClock.getAsLong();
        // Keep recording after temporary writer backpressure. Sequence gaps
        // identify the dropped samples instead of silently ending the session.
        if (!sink.test(batch)) { log.debug("Movement trace batch dropped: writer did not accept it"); }
    }

    private static synchronized boolean submit(List<Entry> batch, Directory directoryProvider)
    {
        if (queuedBatches >= 4) { return false; }
        ++queuedBatches;
        writer = writer.handleAsync((unused, failure) ->
        {
            try
            {
                StringBuilder text = new StringBuilder(batch.size() * 350);
                for (Entry entry : batch) { text.append(entry.line()); }
                // Resolution can migrate legacy logs, so it must stay on the
                // ordered writer too. Only the plugin supplies this capability.
                Filepath directory = directoryProvider.get();
                directory.createDirectories();
                Filepath file = directory.joinSegment("movement.log");
                if (file.exists() && file.size() >= MAX_FILE_BYTES)
                {
                    for (int archive = ARCHIVES; archive >= 2; --archive)
                    {
                        Filepath previous = directory.joinSegment(archiveName(archive - 1));
                        if (previous.exists())
                        {
                            previous.moveTo(directory.joinSegment(archiveName(archive)), StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                    file.moveTo(directory.joinSegment(archiveName(1)), StandardCopyOption.REPLACE_EXISTING);
                }
                file.write(text.toString(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
            catch (IOException | RuntimeException error) { log.debug("Unable to write movement trace", error); }
            finally { synchronized (MovementTrace.class) { --queuedBatches; } }
            return null;
        });
        return true;
    }

    private static String archiveName(int index)
    {
        return index == 1 ? "movement.previous.log" : "movement.previous." + index + ".log";
    }

    abstract static class Entry
    {
        final long session, sequence, micros;

        Entry(long session, long sequence, long micros)
        {
            this.session = session; this.sequence = sequence; this.micros = micros;
        }

        final String identity()
        {
            return " session=" + session + " seq=" + sequence + " tUs=" + micros;
        }

        final String timestamp()
        {
            return " time=" + TIMESTAMP.format(Instant.ofEpochMilli(session + micros / 1000)) + System.lineSeparator();
        }

        abstract String line();
    }

    static final class Sample extends Entry
    {
        final long clickSequence, routeSequence, started;
        final MovementPath.TraceSnapshot route;
        final int cycle, scene, vx, vy, ax, ay, nx, ny, queue, orientation, pose, frame, action;
        final int dx, dy, clickCycle;
        final String startDecision;
        final String phase;
        final boolean moving, running, nativeRender;
        final boolean interactionApproach;
        final String clickAction;
        final int clickTarget;
        final boolean canContinue, inputPending, spot;
        final long inputAgeMicros;
        final int minX, minY, maxX, maxY, goalX, goalY;
        final int npcMinX, npcMinY, npcMaxX, npcMaxY;
        final int npcReserveTiles, weaponId;
        final String combatPhase;
        final boolean combatEngaged;
        final boolean combatLocked, combatHit, combatTrailing;
        final int combatFaceX, combatFaceY;
        final boolean combatWalk;
        final int combatCategory, combatStyle;
        final String combatCacheStyle, combatProfile;
        final boolean combatEffectCarry;
        final int actionFrame;

        Sample(long session, long sequence, long micros, int cycle, int scene, LocalPoint visible,
            LocalPoint authority, LocalPoint nativePoint, String phase, int queue, boolean moving,
            boolean running, int orientation, int pose, int frame, int action, boolean nativeRender,
            LocalPoint destination, int clickCycle, String startDecision, boolean interactionApproach, String clickAction, int clickTarget,
            boolean canContinue, boolean inputPending, long inputAgeMicros, boolean spot,
            ObjectApproach object, LocalPoint approachGoal, NpcApproach npc,
            long clickSequence, long routeSequence, MovementPath.TraceSnapshot route, long started,
            String combatPhase, boolean combatEngaged, boolean combatLocked, LocalPoint combatFaceTarget,
            boolean combatHit, boolean combatTrailing, boolean combatWalk, boolean combatEffectCarry, int actionFrame)
        {
            super(session, sequence, micros);
            this.clickSequence = clickSequence; this.routeSequence = routeSequence;
            this.route = route; this.started = started;
            this.cycle = cycle; this.scene = scene;
            vx = x(visible); vy = y(visible); ax = x(authority); ay = y(authority); nx = x(nativePoint); ny = y(nativePoint);
            this.phase = phase; this.queue = queue; this.moving = moving; this.running = running;
            this.orientation = orientation; this.pose = pose; this.frame = frame; this.action = action; this.nativeRender = nativeRender;
            dx = x(destination); dy = y(destination);
            this.clickCycle = clickCycle; this.startDecision = startDecision;
            this.interactionApproach = interactionApproach;
            this.clickAction = clickAction; this.clickTarget = clickTarget;
            this.canContinue = canContinue; this.inputPending = inputPending;
            this.inputAgeMicros = inputAgeMicros; this.spot = spot;
            minX = x(object == null ? null : object.min); minY = y(object == null ? null : object.min);
            maxX = x(object == null ? null : object.max); maxY = y(object == null ? null : object.max);
            goalX = x(approachGoal); goalY = y(approachGoal);
            npcMinX = x(npc == null ? null : npc.min); npcMinY = y(npc == null ? null : npc.min);
            npcMaxX = x(npc == null ? null : npc.max); npcMaxY = y(npc == null ? null : npc.max);
            npcReserveTiles = npc == null ? -1 : npc.reserveTiles;
            weaponId = npc == null || npc.combat == null ? -1 : npc.combat.weaponId;
            this.combatPhase = combatPhase; this.combatEngaged = combatEngaged;
            this.combatLocked = combatLocked; this.combatHit = combatHit; this.combatTrailing = combatTrailing;
            combatFaceX = x(combatFaceTarget); combatFaceY = y(combatFaceTarget);
            this.combatWalk = combatWalk;
            this.combatEffectCarry = combatEffectCarry; this.actionFrame = actionFrame;
            CombatApproach profile = npc == null ? null : npc.combat;
            combatCategory = profile == null ? -1 : profile.category;
            combatStyle = profile == null ? -1 : profile.style;
            combatCacheStyle = profile == null ? "none" : profile.selectedStyle;
            combatProfile = profile == null ? "none" : profile.adjacentMelee ? "melee" : profile.knownRanged ? "ranged" : "conservative";
        }

        private static int x(LocalPoint p) { return p == null ? -1 : p.getX(); }
        private static int y(LocalPoint p) { return p == null ? -1 : p.getY(); }

        String line()
        {
            String routeLine = route == null ? "" : "[RESPONSIVE-MOVEMENT-ROUTE]" + identity() +
                " clickSeq=" + clickSequence + " routeSeq=" + routeSequence + route.fields(started) + timestamp();
            return routeLine + "[RESPONSIVE-MOVEMENT]" + identity() +
                " cycle=" + cycle + " scene=" + scene + " draw=" + vx + "," + vy + " true=" + ax + "," + ay +
                " native=" + nx + "," + ny + " phase=" + phase + " queue=" + queue + " moving=" + moving +
                " running=" + running + " orientation=" + orientation + " pose=" + pose + " frame=" + frame +
                " action=" + action + " nativeRender=" + nativeRender + " destination=" + dx + "," + dy +
                " trueTileIndicator=" + ax + "," + ay +
                " clickCycle=" + clickCycle + " startDecision=" + startDecision +
                " interaction=" + interactionApproach + " clickAction=" + clickAction + " clickTarget=" + clickTarget +
                " canContinue=" + canContinue + " inputPending=" + inputPending +
                " inputAgeUs=" + inputAgeMicros + " spot=" + spot +
                " objectMin=" + minX + "," + minY + " objectMax=" + maxX + "," + maxY +
                " approachGoal=" + goalX + "," + goalY +
                " npcMin=" + npcMinX + "," + npcMinY + " npcMax=" + npcMaxX + "," + npcMaxY +
                " npcReserveTiles=" + npcReserveTiles + " weaponId=" + weaponId +
                " combatPhase=" + combatPhase + " combatEngaged=" + combatEngaged +
                " combatLocked=" + combatLocked + " combatFaceTarget=" + combatFaceX + "," + combatFaceY +
                " combatHit=" + combatHit + " combatPacing=" + (combatTrailing ? "trailing" : "normal") +
                " combatWalk=" + combatWalk + " combatCategory=" + combatCategory + " combatStyle=" + combatStyle +
                " combatCacheStyle=" + combatCacheStyle + " combatProfile=" + combatProfile +
                " combatEffectCarry=" + combatEffectCarry + " actionFrame=" + actionFrame +
                " clickSeq=" + clickSequence + " routeSeq=" + routeSequence + timestamp();
        }
    }
}
