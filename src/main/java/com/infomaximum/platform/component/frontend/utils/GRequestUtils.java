package com.infomaximum.platform.component.frontend.utils;

import com.infomaximum.cluster.graphql.struct.GRequest;

public class GRequestUtils {

	public static String getHashRequest(GRequest gRequest) {
		return "r" + gRequest.hashCode();
	}
}
