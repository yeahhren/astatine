package net.irisshaders.iris.gui;

import org.jetbrains.annotations.Nullable;

import java.awt.FileDialog;
import java.awt.Frame;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class FileDialogUtil {
	private FileDialogUtil() {
	}

	/**
	 * Opens an asynchronous file select dialog window.
	 *
	 * @param dialog      Whether to open a "save" dialog or an "open" dialog
	 * @param title       The title of the dialog window
	 * @param origin      The path that the window should start at
	 * @param filterLabel A label used to describe what file extensions are allowed and their purpose
	 * @param filters     The file extension filters used by the dialog, each formatted as {@code "*.extension"}
	 * @return a {@link CompletableFuture} which is completed once a file is selected or the dialog is cancelled.
	 */
	public static CompletableFuture<Optional<Path>> fileSelectDialog(DialogType dialog, String title, @Nullable Path origin,
		@Nullable String filterLabel, String... filters) {
		return CompletableFuture.supplyAsync(() -> {
			FileDialog fileDialog = new FileDialog((Frame) null, title, dialog == DialogType.SAVE ? FileDialog.SAVE : FileDialog.LOAD);

			if (origin != null) {
				Path absolute = origin.toAbsolutePath();
				fileDialog.setDirectory(absolute.getParent() == null ? null : absolute.getParent().toString());
				fileDialog.setFile(absolute.getFileName() == null ? null : absolute.getFileName().toString());
			}

			if (filters.length > 0) {
				fileDialog.setFilenameFilter((directory, name) -> {
					for (String filter : filters) {
						String extension = toExtension(filter);

						if (extension.equals("*") || name.endsWith("." + extension)) {
							return true;
						}
					}

					return false;
				});
			}

			fileDialog.setVisible(true);
			String file = fileDialog.getFile();
			String directory = fileDialog.getDirectory();
			fileDialog.dispose();

			if (file == null) {
				return Optional.empty();
			}

			return Optional.of(directory == null ? Paths.get(file) : Paths.get(directory, file));
		});
	}

	private static String toExtension(String filter) {
		if (filter.equals("*") || filter.equals("*.*")) {
			return "*";
		}

		if (filter.startsWith("*.")) {
			return filter.substring(2);
		}

		return filter;
	}

	public enum DialogType {
		SAVE,
		OPEN
	}
}
