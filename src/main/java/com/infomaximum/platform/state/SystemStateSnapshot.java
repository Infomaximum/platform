package com.infomaximum.platform.state;

import com.infomaximum.platform.exception.PlatformException;

/**
 * Immutable-снимок состояния жизненного цикла системы.
 *
 * @param state текущая фаза жизненного цикла
 * @param done  сколько компонентов уже обработано в рамках текущей фазы
 * @param total сколько компонентов нужно обработать в рамках текущей фазы
 * @param error исходное исключение, если {@code state == FAILED}; иначе {@code null}
 */
public record SystemStateSnapshot(SystemState state,
                                  int done,
                                  int total,
                                  PlatformException error) {
}
