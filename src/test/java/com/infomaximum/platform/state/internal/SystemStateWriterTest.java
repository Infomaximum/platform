package com.infomaximum.platform.state.internal;

import com.infomaximum.platform.exception.PlatformException;
import com.infomaximum.platform.sdk.exception.GeneralExceptionBuilder;
import com.infomaximum.platform.state.SystemState;
import com.infomaximum.platform.state.SystemStateSnapshot;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Тесты на {@link SystemStateWriter}: проверка переходов фаз, инкремента прогресса
 * и атомарности read-modify-write под нагрузкой.
 */
public class SystemStateWriterTest {

    /** Изначально writer находится в фазе INITIALIZING с нулевым прогрессом и без ошибки. */
    @Test
    public void initialSnapshotIsInitializing() {
        SystemStateWriter writer = new SystemStateWriter();

        SystemStateSnapshot snapshot = writer.snapshot();

        assertThat(snapshot.state()).isEqualTo(SystemState.INITIALIZING);
        assertThat(snapshot.done()).isZero();
        assertThat(snapshot.total()).isZero();
        assertThat(snapshot.error()).isNull();
    }

    /** setPhase меняет фазу, выставляет total и сбрасывает done/error. */
    @Test
    public void setPhaseUpdatesStateAndResetsProgress() {
        SystemStateWriter writer = new SystemStateWriter();
        writer.incrementDone();
        writer.markFailed(GeneralExceptionBuilder.buildAccessDeniedException());

        writer.setPhase(SystemState.STARTING, 7);

        SystemStateSnapshot snapshot = writer.snapshot();
        assertThat(snapshot.state()).isEqualTo(SystemState.STARTING);
        assertThat(snapshot.done()).isZero();
        assertThat(snapshot.total()).isEqualTo(7);
        assertThat(snapshot.error()).isNull();
    }

    /** incrementDone последовательно увеличивает счётчик в пределах total. */
    @Test
    public void incrementDoneCountsUp() {
        SystemStateWriter writer = new SystemStateWriter();
        writer.setPhase(SystemState.STARTING, 3);

        writer.incrementDone();
        writer.incrementDone();

        SystemStateSnapshot snapshot = writer.snapshot();
        assertThat(snapshot.state()).isEqualTo(SystemState.STARTING);
        assertThat(snapshot.done()).isEqualTo(2);
        assertThat(snapshot.total()).isEqualTo(3);
    }

    /** markReady выставляет READY и сохраняет накопленный прогресс. */
    @Test
    public void markReadyPreservesProgress() {
        SystemStateWriter writer = new SystemStateWriter();
        writer.setPhase(SystemState.STARTING, 4);
        writer.incrementDone();
        writer.incrementDone();
        writer.incrementDone();
        writer.incrementDone();

        writer.markReady();

        SystemStateSnapshot snapshot = writer.snapshot();
        assertThat(snapshot.state()).isEqualTo(SystemState.READY);
        assertThat(snapshot.done()).isEqualTo(4);
        assertThat(snapshot.total()).isEqualTo(4);
        assertThat(snapshot.error()).isNull();
    }

    /** markFailed выставляет FAILED, сохраняет прогресс и сохраняет исходное исключение. */
    @Test
    public void markFailedCapturesError() {
        SystemStateWriter writer = new SystemStateWriter();
        writer.setPhase(SystemState.UPGRADING, 5);
        writer.incrementDone();
        writer.incrementDone();
        PlatformException error = GeneralExceptionBuilder.buildAccessDeniedException();

        writer.markFailed(error);

        SystemStateSnapshot snapshot = writer.snapshot();
        assertThat(snapshot.state()).isEqualTo(SystemState.FAILED);
        assertThat(snapshot.done()).isEqualTo(2);
        assertThat(snapshot.total()).isEqualTo(5);
        assertThat(snapshot.error()).isSameAs(error);
    }

    /**
     * markFailed(null) допустим — состояние переходит в FAILED, error остаётся null.
     * Фиксирует контракт «null разрешён» и защищает от непреднамеренного добавления
     * NPE-guard в реализацию.
     */
    @Test
    public void markFailedWithNullErrorIsAllowed() {
        SystemStateWriter writer = new SystemStateWriter();
        writer.setPhase(SystemState.STARTING, 3);
        writer.incrementDone();

        writer.markFailed(null);

        SystemStateSnapshot snapshot = writer.snapshot();
        assertThat(snapshot.state()).isEqualTo(SystemState.FAILED);
        assertThat(snapshot.done()).isEqualTo(1);
        assertThat(snapshot.total()).isEqualTo(3);
        assertThat(snapshot.error()).isNull();
    }

    /**
     * Параллельные incrementDone из нескольких тредов не теряют инкременты —
     * AtomicReference.updateAndGet обеспечивает корректность read-modify-write.
     */
    @Test
    public void concurrentIncrementsAreLossless() throws InterruptedException {
        SystemStateWriter writer = new SystemStateWriter();
        int threadCount = 16;
        int incrementsPerThread = 1000;
        writer.setPhase(SystemState.STARTING, threadCount * incrementsPerThread);

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    start.await();
                    for (int j = 0; j < incrementsPerThread; j++) {
                        writer.incrementDone();
                    }
                } catch (InterruptedException ignore) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }).start();
        }
        start.countDown();
        done.await();

        assertThat(writer.snapshot().done()).isEqualTo(threadCount * incrementsPerThread);
    }
}
