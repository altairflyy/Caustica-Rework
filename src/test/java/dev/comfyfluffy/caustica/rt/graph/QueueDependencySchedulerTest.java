package dev.comfyfluffy.caustica.rt.graph;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QueueDependencySchedulerTest {
    @Test
    void publishedTransferBuildWaitsAtTheExistingGraphicsBoundary() {
        QueueDependencyScheduler scheduler = new QueueDependencyScheduler();
        long first = scheduler.reserveTransferBuild();
        long second = scheduler.reserveTransferBuild();

        scheduler.markTransferBuildSubmitted(second);
        scheduler.publishTransferBuild(first);
        assertEquals(new QueueDependencyScheduler.DependencyRoute(
                QueueDependencyScheduler.QueueLane.COMPUTE,
                QueueDependencyScheduler.WorkKind.TRANSFER_BUILD,
                QueueDependencyScheduler.QueueLane.GRAPHICS,
                QueueDependencyScheduler.Timeline.BUILD_COMPLETION),
                QueueDependencyScheduler.TRANSFER_BUILD_TO_GRAPHICS);
        assertEquals(first, scheduler.publishedTransferBuildWaitValue());
        assertEquals(1L, scheduler.reserveGraphicsFrameCompletion());
        assertEquals(second, scheduler.submittedTransferBuildValue());
    }

    @Test
    void unpublishedWorkDoesNotReduceReferenceOverlap() {
        QueueDependencyScheduler scheduler = new QueueDependencyScheduler();
        scheduler.reserveTransferBuild();

        assertEquals(0L, scheduler.publishedTransferBuildWaitValue());
        assertEquals(1L, scheduler.reserveGraphicsFrameCompletion());
    }

    @Test
    void publicationCoalescesToTheNewestTimelineValueWithoutAddingDependencies() {
        QueueDependencyScheduler scheduler = new QueueDependencyScheduler();
        long first = scheduler.reserveTransferBuild();
        long second = scheduler.reserveTransferBuild();
        scheduler.publishTransferBuild(second);
        scheduler.publishTransferBuild(first);

        assertEquals(second, scheduler.publishedTransferBuildWaitValue());
        assertEquals(second, scheduler.publishedTransferBuildWaitValue());
    }

    @Test
    void graphicsCompletionTracksOnlySignalledFrames() {
        QueueDependencyScheduler scheduler = new QueueDependencyScheduler();
        long first = scheduler.reserveGraphicsFrameCompletion();
        long second = scheduler.reserveGraphicsFrameCompletion();

        assertEquals(0L, scheduler.latestGraphicsCompletionValue());
        scheduler.completeGraphicsFrame(first);
        assertEquals(first, scheduler.latestGraphicsCompletionValue());
        scheduler.completeGraphicsFrame(second);
        assertEquals(second, scheduler.latestGraphicsCompletionValue());
        assertEquals(new QueueDependencyScheduler.DependencyRoute(
                        QueueDependencyScheduler.QueueLane.GRAPHICS,
                        QueueDependencyScheduler.WorkKind.FRAME,
                        QueueDependencyScheduler.QueueLane.GRAPHICS,
                        QueueDependencyScheduler.Timeline.GRAPHICS_COMPLETION),
                QueueDependencyScheduler.GRAPHICS_FRAME_REUSE);
        assertEquals(second, scheduler.requireGraphicsCompletionValue(second));
    }

    @Test
    void rejectsTimelineValuesThatWereNeverReserved() {
        QueueDependencyScheduler scheduler = new QueueDependencyScheduler();

        assertThrows(IllegalArgumentException.class, () -> scheduler.publishTransferBuild(1L));
        assertThrows(IllegalArgumentException.class, () -> scheduler.markTransferBuildSubmitted(1L));
        assertThrows(IllegalArgumentException.class, () -> scheduler.completeGraphicsFrame(1L));
    }
}
