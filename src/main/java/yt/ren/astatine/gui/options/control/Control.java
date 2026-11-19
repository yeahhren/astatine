package yt.ren.astatine.gui.options.control;

import yt.ren.astatine.gui.Dim2i;
import yt.ren.astatine.gui.options.Option;

public interface Control<T> {
	Option<T> getOption();

	ControlElement<T> createElement(Dim2i dim);

	int getMaxWidth();
}
