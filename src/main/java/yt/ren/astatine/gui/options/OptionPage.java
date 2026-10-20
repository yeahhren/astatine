package yt.ren.astatine.gui.options;

import java.util.List;

public final class OptionPage {
	private final String name;
	private final List<OptionGroup> groups;
	private final List<Option<?>> options;

	public OptionPage(String name, List<OptionGroup> groups) {
		this.name = name;
		this.groups = List.copyOf(groups);
		this.options = this.groups.stream().flatMap(group -> group.getOptions().stream()).toList();
	}

	public List<OptionGroup> getGroups() {
		return this.groups;
	}

	public List<Option<?>> getOptions() {
		return this.options;
	}

	public String getName() {
		return this.name;
	}
}
