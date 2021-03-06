package com.infomaximum.platform.sdk.utils;

import com.infomaximum.platform.sdk.exception.GeneralExceptionBuilder;
import com.infomaximum.subsystems.exception.SubsystemException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileUtils {

	public static void ensureDirectory(Path dir) throws SubsystemException {
		try {
			if (!Files.exists(dir) || !Files.isDirectory(dir)) {
				Files.createDirectory(dir);
			}
		} catch (IOException e) {
			throw GeneralExceptionBuilder.buildIOErrorException(e);
		} catch (SecurityException e) {
			throw GeneralExceptionBuilder.buildSecurityException(e);
		}
	}

}
