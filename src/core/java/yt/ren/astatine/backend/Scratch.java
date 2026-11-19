package yt.ren.astatine.backend;

import yt.ren.astatine.memory.Memory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class Scratch {
	private ByteBuffer buffer;

	public Scratch(int capacity) {
		this.buffer = ByteBuffer.allocateDirect(Math.max(64, capacity)).order(ByteOrder.nativeOrder());
	}

	public Scratch() {
		this(1024);
	}

	public Scratch clear() {
		this.buffer.clear();
		return this;
	}

	private void ensure(int bytes) {
		if (this.buffer.remaining() >= bytes) {
			return;
		}

		var grown = ByteBuffer.allocateDirect(Math.max(this.buffer.capacity() * 2, this.buffer.position() + bytes))
			.order(ByteOrder.nativeOrder());
		this.buffer.flip();
		grown.put(this.buffer);
		this.buffer = grown;
	}

	public Scratch putInt(int value) {
		this.ensure(4);
		this.buffer.putInt(value);
		return this;
	}

	public Scratch putLong(long value) {
		this.ensure(8);
		this.buffer.putLong(value);
		return this;
	}

	public Scratch putFloat(float value) {
		this.ensure(4);
		this.buffer.putFloat(value);
		return this;
	}

	public Scratch putBytes(ByteBuffer data) {
		this.ensure(data.remaining());
		this.buffer.put(data.duplicate());
		return this;
	}

	public Scratch putIntAt(int index, int value) {
		this.buffer.putInt(index, value);
		return this;
	}

	public int position() {
		return this.buffer.position();
	}

	public long address() {
		return Memory.INSTANCE.address(this.buffer);
	}
}
