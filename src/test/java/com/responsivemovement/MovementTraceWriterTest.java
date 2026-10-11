package com.responsivemovement;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.util.Filepath;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

/** Isolated filesystem checks; reflection and Unchecked are test-only. */
public class MovementTraceWriterTest
{
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @After
    public void finishPendingWritesBeforeTemporaryFilesAreRemoved() throws Exception
    {
        awaitWriter();
    }

    @Test
    public void disabledAndEmptyRecordingNeverResolveOrMigrateTheDirectory() throws Exception
    {
        AtomicInteger resolutions = new AtomicInteger();
        MovementTrace trace = new MovementTrace((MovementTrace.Directory) () -> {
            resolutions.incrementAndGet();
            throw new AssertionError("No directory access is allowed without a recorded batch");
        });
        record(trace, 1);
        trace.poll();
        trace.close();
        trace.enabled(true);
        trace.close();
        awaitWriter();
        assertEquals(0, resolutions.get());
    }

    @Test
    public void stopFlushesOrderedSamplesUsingTheManagedProviderOffTheCallingThread() throws Exception
    {
        Filepath directory = directory();
        Thread caller = Thread.currentThread();
        AtomicInteger resolutions = new AtomicInteger();
        MovementTrace trace = new MovementTrace(() -> {
            assertNotSame("directory resolution and migration must stay off the calling thread", caller, Thread.currentThread());
            resolutions.incrementAndGet();
            return directory;
        });
        trace.enabled(true);
        record(trace, 1);
        record(trace, 2);
        assertEquals("partial batches wait for a flush", 0, resolutions.get());
        trace.close();
        awaitWriter();
        String text = read(directory.joinSegment("movement.log"));
        assertEquals(1, resolutions.get());
        assertTrue(text.contains("seq=0"));
        assertTrue(text.contains("seq=1"));
        assertTrue(text.indexOf("seq=0") < text.indexOf("seq=1"));
    }

    @Test
    public void bookmarkFlushesThroughTheSameOffThreadManagedWriterInSampleOrder() throws Exception
    {
        Filepath directory = directory();
        Thread caller = Thread.currentThread();
        MovementTrace trace = new MovementTrace(() -> {
            assertNotSame(caller, Thread.currentThread());
            return directory;
        });
        NpcApproachControllerTest.Fixture f = new NpcApproachControllerTest.Fixture(5952, 5952, 7000, 7000);
        try
        {
            trace.enabled(true); record(trace, 1);
            ResponsiveMovementConfig config = new ResponsiveMovementConfig() {
                @Override public boolean recordTrace() { return true; }
            };
            assertEquals(1, trace.bookmark(f.client, config, 0, "button", System.nanoTime(), f.start, null));
            awaitWriter();
            String text = read(directory.joinSegment("movement.log"));
            assertTrue(text.contains("[RESPONSIVE-MOVEMENT-BOOKMARK]"));
            assertTrue(text.contains("source=button"));
            assertTrue(text.indexOf("seq=0") < text.indexOf("seq=1"));
        }
        finally { trace.close(); f.controller.close(); }
    }

    @Test
    public void rotationPreservesTheCurrentLogAndBoundsTheArchiveCount() throws Exception
    {
        Filepath directory = directory();
        directory.createDirectories();
        directory.joinSegment("movement.log").write(new byte[8 * 1024 * 1024]);
        for (int i = 1; i <= 7; ++i)
        {
            directory.joinSegment(i == 1 ? "movement.previous.log" : "movement.previous." + i + ".log")
                .write("archive-" + i);
        }
        MovementTrace trace = new MovementTrace(() -> directory);
        trace.enabled(true);
        record(trace, 3);
        trace.close();
        awaitWriter();
        assertEquals(8L * 1024 * 1024, directory.joinSegment("movement.previous.log").size());
        assertEquals("archive-1", read(directory.joinSegment("movement.previous.2.log")));
        assertEquals("archive-6", read(directory.joinSegment("movement.previous.7.log")));
        assertTrue(read(directory.joinSegment("movement.log")).contains("cycle=3"));
        try (java.util.stream.Stream<Filepath> files = directory.walk())
        {
            assertEquals(8, files.filter(Filepath::isFile).count());
        }
    }

    @Test
    public void aDirectoryFailureReleasesWriterCapacityAndLaterSessionsCanResume() throws Exception
    {
        Filepath directory = directory();
        AtomicInteger attempts = new AtomicInteger();
        MovementTrace trace = new MovementTrace(() -> {
            if (attempts.incrementAndGet() <= 5) { throw new IOException("Directory unavailable"); }
            return directory;
        });
        for (int cycle = 1; cycle <= 6; ++cycle)
        {
            trace.enabled(true);
            record(trace, cycle);
            trace.close();
            awaitWriter();
        }
        assertEquals(6, attempts.get());
        assertTrue(read(directory.joinSegment("movement.log")).contains("cycle=6"));
    }

    private Filepath directory()
    {
        // Only tests construct rooted paths directly; production receives a
        // capability from RuneLite's Plugin.getPluginDirectory().
        return Filepath.Unchecked.getRooted(temporary.getRoot().toPath()).joinSegment("managed");
    }

    private static String read(Filepath file) throws IOException
    {
        try (java.io.BufferedReader reader = file.openBufferedReader())
        {
            return reader.lines().collect(java.util.stream.Collectors.joining("\n"));
        }
    }

    private static void record(MovementTrace trace, int cycle)
    {
        LocalPoint point = new LocalPoint(5952, 5952, 0);
        trace.record(cycle, 0, point, point, point, null, 0, -1, 0, -1, true,
            null, cycle, "started", false, "WALK", -1, false, false, -1, false);
    }

    private static void awaitWriter() throws Exception
    {
        // Observe completion only in tests. Production shutdown never waits.
        Field field = MovementTrace.class.getDeclaredField("writer");
        field.setAccessible(true);
        ((CompletableFuture<?>) field.get(null)).get(10, TimeUnit.SECONDS);
    }
}
