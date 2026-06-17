package com.infomaximum.platform.component.frontend.engine.download;

import com.infomaximum.platform.sdk.component.Component;
import com.infomaximum.platform.sdk.graphql.out.GOutputFile;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Тесты {@link DownloadStore}: одноразовая выдача файла по токену между HEAD и GET.
 * Сброс крупного {@code byte[]} на диск (через {@code ClusterFile}) здесь не покрывается —
 * требует реальной файловой инфраструктуры, проверяется на живом сервере.
 */
class DownloadStoreTest {

    private final DownloadStore store = new DownloadStore(mock(Component.class));

    private static GOutputFile file(String name, String body) {
        return new GOutputFile.Builder(name, body.getBytes(StandardCharsets.UTF_8)).build();
    }

    /** Файл, положенный под токен, забирается тем же токеном. */
    @Test
    void takeReturnsStoredFileByToken() {
        GOutputFile f = file("report.csv", "data");
        String token = store.put(f);

        assertThat(token).isNotBlank();
        assertThat(store.take(token)).isSameAs(f);
    }

    /** Токен одноразовый: повторное забирание возвращает {@code null}. */
    @Test
    void tokenIsSingleUse() {
        String token = store.put(file("report.csv", "data"));

        assertThat(store.take(token)).isNotNull();
        assertThat(store.take(token)).isNull();
    }

    /** Неизвестный токен → {@code null} (на стороне контроллера превращается в 404). */
    @Test
    void unknownTokenReturnsNull() {
        assertThat(store.take("no-such-token")).isNull();
    }

    /** Мелкий {@code byte[]} не сбрасывается на диск — остаётся как есть в памяти. */
    @Test
    void smallBodyKeptInMemory() {
        GOutputFile f = file("small.txt", "tiny");
        GOutputFile taken = store.take(store.put(f));

        assertThat(taken).isSameAs(f);
        assertThat(taken.body).isNotNull();
        assertThat(taken.uri).isNull();
    }
}
