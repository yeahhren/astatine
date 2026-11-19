package net.irisshaders.iris.shaderpack;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class PackDirectories {
	private PackDirectories() {
	}

	public static Stream<Path> list(Path directory) throws IOException {
		if (Files.isDirectory(directory) || !Files.exists(directory) || !"jar".equals(directory.getFileSystem().provider().getScheme())) {
			return Files.list(directory);
		}

		// zipfs URIs look like jar:file:///path/to/pack.zip!/shaders
		String uri = directory.toUri().getRawSchemeSpecificPart();
		int separator = uri.lastIndexOf("!/");
		if (separator < 0) {
			return Files.list(directory);
		}

		FileSystem fileSystem = directory.getFileSystem();
		String prefix = directory.toAbsolutePath().toString().substring(1) + "/";
		Set<Path> children = new LinkedHashSet<>();

		try (ZipFile zip = new ZipFile(Path.of(URI.create(uri.substring(0, separator))).toFile())) {
			for (ZipEntry entry : Collections.list(zip.entries())) {
				String name = entry.getName();
				if (!name.startsWith(prefix) || name.length() == prefix.length()) {
					continue;
				}

				String rest = name.substring(prefix.length());
				int slash = rest.indexOf('/');
				children.add(fileSystem.getPath(prefix + (slash < 0 ? rest : rest.substring(0, slash))));
			}
		}

		return children.stream();
	}
}
