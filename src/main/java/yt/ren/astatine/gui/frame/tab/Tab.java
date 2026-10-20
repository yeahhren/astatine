package yt.ren.astatine.gui.frame.tab;

import yt.ren.astatine.gui.Dim2i;
import yt.ren.astatine.gui.frame.AbstractFrame;
import yt.ren.astatine.gui.frame.OptionPageFrame;
import yt.ren.astatine.gui.frame.ScrollableFrame;
import yt.ren.astatine.gui.options.OptionPage;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public record Tab<T extends AbstractFrame>(String title, Function<Dim2i, T> frameFunction) {
	public static Tab.Builder<?> createBuilder() {
		return new Tab.Builder<>();
	}

	public String getTitle() {
		return this.title;
	}

	public Function<Dim2i, T> getFrameFunction() {
		return this.frameFunction;
	}

	public static class Builder<T extends AbstractFrame> {
		private String title;
		private Function<Dim2i, T> frameFunction;

		public Builder<T> setTitle(String title) {
			this.title = title;
			return this;
		}

		public Builder<T> setFrameFunction(Function<Dim2i, T> frameFunction) {
			this.frameFunction = frameFunction;
			return this;
		}

		public Tab<T> build() {
			return new Tab<>(this.title, this.frameFunction);
		}

		public Tab<ScrollableFrame> from(OptionPage page, AtomicReference<Integer> verticalScrollBarOffset) {
			return new Tab<>(page.getName(), dim2i -> ScrollableFrame
				.createBuilder()
				.setDimension(dim2i)
				.setFrame(OptionPageFrame
					.createBuilder()
					.setDimension(new Dim2i(dim2i.x(), dim2i.y(), dim2i.width(), dim2i.height()))
					.setOptionPage(page)
					.build())
				.setVerticalScrollBarOffset(verticalScrollBarOffset)
				.build());
		}
	}
}
