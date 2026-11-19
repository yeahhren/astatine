package com.mojang.renderpearl.api.device;

public record DeviceLimits(int maxAnisotropy, int minUniformOffsetAlignment, int maxTextureSize, long maxMemoryAllocationSize, int maxColorAttachments) {
}
