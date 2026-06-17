package com.infomaximum.platform.component.frontend.engine.download;

import com.infomaximum.cluster.core.io.ClusterFile;
import com.infomaximum.platform.sdk.component.Component;
import com.infomaximum.platform.sdk.graphql.out.GOutputFile;
import com.infomaximum.platform.utils.TimedCache;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

/**
 * Хранилище подготовленных к скачиванию файлов между запросом-подготовкой (HEAD) и
 * запросом-отдачей (GET). HEAD кладёт сюда сформированный {@link GOutputFile} и получает
 * непредсказуемый одноразовый токен; GET по токену забирает файл и отдаёт его, не исполняя
 * GraphQL-операцию повторно. Это снимает двойную генерацию файла и позволяет GET-навигации
 * скачивать файл без заголовка {@code X-CSRF-Token} (токен-«пропуск» вместо CSRF).
 *
 * <p>Токен — capability: владение им есть право забрать один конкретный файл. Записи живут
 * ограниченное время ({@link #TTL_MS}); просроченные вытесняются, и временный файл при этом
 * удаляется (колбэк вытеснения). Крупный {@code byte[]} сбрасывается во временный файл, чтобы
 * не удерживать его в оперативной памяти.
 *
 * <p>Рассчитано на один фронт-узел: HEAD и GET приходят на одну ноду, файл локален.
 */
public class DownloadStore {

    private static final Logger log = LoggerFactory.getLogger(DownloadStore.class);

    /** Время жизни подготовленного файла между HEAD и GET. */
    private static final long TTL_MS = Duration.ofMinutes(1).toMillis();
    /** Порог: {@code byte[]} крупнее сбрасывается во временный файл (не держим в RAM). */
    private static final int SPILL_THRESHOLD_BYTES = 16 * 1024 * 1024;
    private static final int TOKEN_BYTES = 32;

    private final Component component;
    private final SecureRandom random = new SecureRandom();
    private final TimedCache<String, GOutputFile> store = new TimedCache<>(TTL_MS, DownloadStore::deleteIfTemp);

    public DownloadStore(@NonNull Component component) {
        this.component = component;
    }

    /**
     * Сохраняет файл и возвращает токен для последующей отдачи. Крупный {@code byte[]}
     * предварительно сбрасывается во временный файл.
     *
     * @param file подготовленный к скачиванию файл.
     * @return непредсказуемый одноразовый токен.
     */
    public @NonNull String put(@NonNull GOutputFile file) {
        GOutputFile stored = spillIfLarge(file);
        String token = newToken();
        store.put(token, stored);
        return token;
    }

    /**
     * Забирает файл по токену (одноразово). Повторный вызов с тем же токеном вернёт
     * {@code null}.
     *
     * @param token токен из {@link #put(GOutputFile)}.
     * @return файл либо {@code null}, если токен неизвестен, израсходован или просрочен.
     */
    public @Nullable GOutputFile take(@NonNull String token) {
        return store.poll(token);
    }

    private GOutputFile spillIfLarge(GOutputFile file) {
        if (file.body == null || file.body.length <= SPILL_THRESHOLD_BYTES) {
            return file;
        }
        try {
            Path tmp = Files.createTempFile("download-", ".bin");
            Files.write(tmp, file.body);
            return new GOutputFile.Builder(file.fileName, new ClusterFile(component, tmp.toUri()))
                    .withMimeType(file.mimeType)
                    .withTemp(true)
                    .withCache(file.cache)
                    .build();
        } catch (IOException e) {
            log.warn("Не удалось сбросить download-файл на диск, остаётся в памяти", e);
            return file;
        }
    }

    private String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static void deleteIfTemp(GOutputFile file) {
        if (file.temp && file.uri != null) {
            try {
                Files.deleteIfExists(Paths.get(file.uri));
            } catch (IOException e) {
                log.warn("Не удалось удалить просроченный download-файл", e);
            }
        }
    }
}
