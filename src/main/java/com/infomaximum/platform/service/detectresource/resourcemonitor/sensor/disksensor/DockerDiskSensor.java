package com.infomaximum.platform.service.detectresource.resourcemonitor.sensor.disksensor;

import com.infomaximum.platform.prometheus.metric.FilesystemSizeMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.function.BinaryOperator;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;

public class DockerDiskSensor implements DiskSpaceSensor {
    private final Path directory;
    private final FileSystem fileSystem;
    private Optional<FileStore> fileStore;
    private final Logger log = LoggerFactory.getLogger(DockerDiskSensor.class);
    private Predicate<FileStore> storeFilter = o -> false;

    public DockerDiskSensor(Path directory) {
        this.directory = directory;
        fileSystem = directory.getFileSystem();
        if(Files.exists(directory)) {
            storeFilter = fs -> directory.startsWith(fs.toString().replaceFirst("\\s\\(.+", ""));
        }
        updateFileStore();
    }

    @Override
    public Long getTotalSpace() throws IOException {
        updateFileStore();
        long totalSpace = 0L;
        if(fileStore.isPresent()) {
            totalSpace = fileStore.get().getTotalSpace();
            FilesystemSizeMetric.filesystemSizeMetric.setWithLabelValues(totalSpace, FilesystemSizeMetric.TOTAL);
            return totalSpace;
        }
        return totalSpace;
    }

    @Override
    public Long getFreeSpace() throws IOException {
        updateFileStore();
        long freeSpace = 0L;
        if (fileStore.isPresent()) {
            freeSpace = fileStore.get().getUsableSpace();
            FilesystemSizeMetric.filesystemSizeMetric.setWithLabelValues(freeSpace, FilesystemSizeMetric.FREE);
            return freeSpace;
        }
        return freeSpace;
    }

    @Override
    public Long getUsedSpace() throws IOException {
        updateFileStore();
        long usedSpace = 0L;
        if (fileStore.isPresent()) {
            usedSpace = fileStore.get().getTotalSpace() - fileStore.get().getUsableSpace();
            FilesystemSizeMetric.filesystemSizeMetric.setWithLabelValues(usedSpace, FilesystemSizeMetric.USAGE);
            return fileStore.get().getTotalSpace() - fileStore.get().getUsableSpace();
        }
        return usedSpace;
    }

    private void updateFileStore() {
        fileStore = StreamSupport.stream(fileSystem.getFileStores().spliterator(), false)
                .filter(storeFilter)
                .reduce(BinaryOperator.maxBy(Comparator.comparing(FileStore::toString)));
        if (fileStore.isEmpty()) {
            log.info("DockerMemorySensor: path {} doesn't found in file system", directory);
        }
    }
}