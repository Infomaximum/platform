package com.infomaximum.platform.state.internal;

import com.infomaximum.platform.exception.PlatformException;
import com.infomaximum.platform.state.SystemState;
import com.infomaximum.platform.state.SystemStateSnapshot;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Внутренний фасад записи в {@link SystemStateSnapshot}.
 */
public final class SystemStateWriter {

    private final AtomicReference<SystemStateSnapshot> snapshot = new AtomicReference<>(
            new SystemStateSnapshot(SystemState.INITIALIZING, 0, 0, null)
    );

    /**
     * Возвращает текущий снимок состояния.
     */
    public SystemStateSnapshot snapshot() {
        return snapshot.get();
    }

    /**
     * Переходит в указанную фазу, сбрасывая прогресс ({@code done = 0}, {@code error = null}).
     *
     * @param state новая фаза
     * @param total общее число компонентов, которые нужно обработать в этой фазе
     */
    public void setPhase(SystemState state, int total) {
        snapshot.set(new SystemStateSnapshot(state, 0, total, null));
    }

    /**
     * Увеличивает счётчик обработанных компонентов на 1.
     * Остальные поля снимка сохраняются.
     */
    public void incrementDone() {
        snapshot.updateAndGet(s -> new SystemStateSnapshot(s.state(), s.done() + 1, s.total(), s.error()));
    }

    /**
     * Помечает систему как успешно запущенную ({@link SystemState#READY}).
     * Прогресс и {@code error} сохраняются как есть.
     */
    public void markReady() {
        snapshot.updateAndGet(s -> new SystemStateSnapshot(SystemState.READY, s.done(), s.total(), s.error()));
    }

    /**
     * Помечает систему как сломанную фатальной ошибкой ({@link SystemState#FAILED}).
     * Прогресс сохраняется, в {@link SystemStateSnapshot#error()} кладётся исходное исключение.
     *
     * @param error исходное исключение, спровоцировавшее переход в {@code FAILED}
     */
    public void markFailed(PlatformException error) {
        snapshot.updateAndGet(s -> new SystemStateSnapshot(SystemState.FAILED, s.done(), s.total(), error));
    }
}
