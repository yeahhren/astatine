package com.mojang.renderpearl.api.commands;

import com.mojang.renderpearl.api.textures.GpuTextureView;
import org.joml.Vector4fc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;

public record RenderPassDescriptor(Supplier<String> label, List<Attachment<Optional<Vector4fc>>> colorAttachments, Attachment<OptionalDouble> depthAttachment, RenderPass.RenderArea renderArea) {
	public RenderPassDescriptor {
		colorAttachments = Collections.unmodifiableList(new ArrayList<>(colorAttachments));
	}

	public static Builder builder(Supplier<String> label) {
		return new Builder(label);
	}

	public record Attachment<T>(GpuTextureView textureView, T clearValue) {
	}

	public static class Builder {
		private final Supplier<String> label;
		private final List<Attachment<Optional<Vector4fc>>> colorAttachments = new ArrayList<>();
		private Attachment<OptionalDouble> depthAttachment;
		private RenderPass.RenderArea renderArea;

		private Builder(Supplier<String> label) {
			this.label = label;
		}

		public Builder withColorAttachment(GpuTextureView textureView) {
			return this.withColorAttachment(textureView, Optional.empty());
		}

		public Builder withColorAttachment(GpuTextureView textureView, Optional<Vector4fc> clearValue) {
			this.colorAttachments.add(new Attachment<>(textureView, clearValue));
			return this;
		}

		public Builder withUnusedColorAttachment() {
			this.colorAttachments.add(null);
			return this;
		}

		public Builder withDepthAttachment(GpuTextureView textureView) {
			return this.withDepthAttachment(textureView, OptionalDouble.empty());
		}

		public Builder withDepthAttachment(GpuTextureView textureView, OptionalDouble clearValue) {
			this.depthAttachment = new Attachment<>(textureView, clearValue);
			return this;
		}

		public Builder withRenderArea(RenderPass.RenderArea renderArea) {
			this.renderArea = renderArea;
			return this;
		}

		public RenderPassDescriptor build() {
			var area = this.renderArea;

			if (area == null) {
				int width = 0;
				int height = 0;

				if (!this.colorAttachments.isEmpty()) {
					for (var attachment : this.colorAttachments) {
						if (attachment != null) {
							width = attachment.textureView().getWidth(0);
							height = attachment.textureView().getHeight(0);
						}
					}
				} else if (this.depthAttachment != null) {
					width = this.depthAttachment.textureView().getWidth(0);
					height = this.depthAttachment.textureView().getHeight(0);
				}

				area = new RenderPass.RenderArea(0, 0, width, height);
			}

			return new RenderPassDescriptor(this.label, this.colorAttachments, this.depthAttachment, area);
		}
	}
}
