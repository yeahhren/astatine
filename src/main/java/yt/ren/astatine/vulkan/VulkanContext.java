package yt.ren.astatine.vulkan;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.FunctionProvider;
import org.lwjgl.system.JNI;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VK;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkApplicationInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkDeviceCreateInfo;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkInstanceCreateInfo;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties;
import org.lwjgl.vulkan.VkQueue;
import yt.ren.astatine.Native;

import java.nio.ByteBuffer;
import java.util.Set;

public final class VulkanContext {
	private static VkInstance instance;
	private static VkPhysicalDevice physicalDevice;
	private static VkDevice device;
	private static VkQueue queue;
	private static int queueFamily;
	private static int apiVersion;
	private static int deviceApiVersion;
	private static Set<String> instanceExtensions;
	private static Set<String> deviceExtensions;
	private static Set<String> deviceFeatures;

	private VulkanContext() {
	}

	public static synchronized void initialize() {
		if (device != null) {
			return;
		}

		long[] handles = Native.vulkanHandles();
		long getInstanceProcAddr = handles[6];
		apiVersion = (int) handles[5];
		queueFamily = (int) handles[4];
		try {
			VK.getFunctionProvider();
		} catch (IllegalStateException missing) {
			createFunctions(getInstanceProcAddr);
		}

		try (MemoryStack stack = MemoryStack.stackPush()) {
			String[] enabledInstanceExtensions = Native.vulkanInstanceExtensions();
			instanceExtensions = Set.of(enabledInstanceExtensions);
			VkApplicationInfo application = VkApplicationInfo.calloc(stack).sType$Default().apiVersion(apiVersion);
			VkInstanceCreateInfo instanceInfo = VkInstanceCreateInfo.calloc(stack).sType$Default().pApplicationInfo(application)
				.ppEnabledExtensionNames(names(stack, enabledInstanceExtensions));
			instance = new VkInstance(handles[0], instanceInfo);
			physicalDevice = new VkPhysicalDevice(handles[1], instance);
			VkPhysicalDeviceProperties properties = VkPhysicalDeviceProperties.calloc(stack);
			VK10.vkGetPhysicalDeviceProperties(physicalDevice, properties);
			deviceApiVersion = Integer.compareUnsigned(properties.apiVersion(), apiVersion) < 0 ? properties.apiVersion() : apiVersion;
			String[] extensions = Native.vulkanDeviceExtensions();
			deviceExtensions = Set.of(extensions);
			deviceFeatures = Set.of(Native.vulkanDeviceFeatures());
			VkDeviceCreateInfo deviceInfo = VkDeviceCreateInfo.calloc(stack).sType$Default()
				.ppEnabledExtensionNames(names(stack, extensions));
			device = new VkDevice(handles[2], physicalDevice, deviceInfo, deviceApiVersion);
			queue = new VkQueue(handles[3], device);
		}
	}

	private static void createFunctions(long getInstanceProcAddr) {
		VK.create(new FunctionProvider() {
			@Override
			public long getFunctionAddress(ByteBuffer name) {
				return JNI.callPPP(MemoryUtil.NULL, MemoryUtil.memAddress(name), getInstanceProcAddr);
			}

			@Override
			public long getFunctionAddress(CharSequence name) {
				try (MemoryStack stack = MemoryStack.stackPush()) {
					return this.getFunctionAddress(stack.ASCII(name));
				}
			}
		});
	}

	private static PointerBuffer names(MemoryStack stack, String[] values) {
		PointerBuffer pointers = stack.mallocPointer(values.length);

		for (String value : values) {
			pointers.put(stack.UTF8(value));
		}

		return pointers.flip();
	}

	public static VkInstance instance() {
		initialize();
		return instance;
	}

	public static VkPhysicalDevice physicalDevice() {
		initialize();
		return physicalDevice;
	}

	public static VkDevice device() {
		initialize();
		return device;
	}

	public static VkQueue queue() {
		initialize();
		return queue;
	}

	public static int queueFamily() {
		initialize();
		return queueFamily;
	}

	public static int apiVersion() {
		initialize();
		return apiVersion;
	}

	public static int deviceApiVersion() {
		initialize();
		return deviceApiVersion;
	}

	public static Set<String> instanceExtensions() {
		initialize();
		return instanceExtensions;
	}

	public static Set<String> deviceExtensions() {
		initialize();
		return deviceExtensions;
	}

	public static Set<String> deviceFeatures() {
		initialize();
		return deviceFeatures;
	}
}
