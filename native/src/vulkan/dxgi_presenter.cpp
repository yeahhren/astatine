#ifdef _WIN32

#include "vulkan/dxgi_presenter.h"

#include "core/error.h"
#include "core/log.h"
#include "vulkan/device.h"

#include <windows.h>
#include <d3d12.h>
#include <dxgi1_6.h>
#include <wrl/client.h>
#include <vulkan/vulkan_win32.h>

#include <cstring>
#include <string>
#include <vector>

using Microsoft::WRL::ComPtr;

namespace at {
    static constexpr u32 BUFFER_COUNT = 3;

    static void hr(HRESULT result, const char *action) {
        if (FAILED(result)) {
            char code[16];
            std::snprintf(code, sizeof(code), "0x%08lX", static_cast<unsigned long>(result));
            fail(std::string(action) + " failed with HRESULT " + code);
        }
    }

    struct DxgiPresenter::State {
        HWND window = nullptr;
        ComPtr<IDXGIFactory4> factory;
        ComPtr<ID3D12Device> d3d;
        ComPtr<ID3D12CommandQueue> queue;
        ComPtr<IDXGISwapChain3> swapchain;
        HANDLE idleEvent = nullptr;
        bool tearing = false;
        HANDLE waitable = nullptr;
        bool vsync = true;
        UINT flags = 0;

        ComPtr<ID3D12Fence> renderedFence;
        ComPtr<ID3D12Fence> copiedFence;
        VkSemaphore rendered = VK_NULL_HANDLE;
        VkSemaphore copied = VK_NULL_HANDLE;
        u64 renderedValue = 0;
        u64 copiedValue = 0;

        struct Buffer {
            ComPtr<ID3D12Resource> back;
            ComPtr<ID3D12Resource> shared;
            ComPtr<ID3D12CommandAllocator> allocator;
            ComPtr<ID3D12GraphicsCommandList> copy;
            VkDeviceMemory memory = VK_NULL_HANDLE;
            u64 copiedAt = 0;
        };

        std::vector<Buffer> buffers;

        PFN_vkGetMemoryWin32HandlePropertiesKHR getMemoryWin32HandleProperties = nullptr;
        PFN_vkImportSemaphoreWin32HandleKHR importSemaphoreWin32Handle = nullptr;
    };

    static VkSemaphore importFence(const Device &device, const DxgiPresenter::State &state, ID3D12Fence *fence) {
        HANDLE handle = nullptr;
        hr(state.d3d->CreateSharedHandle(fence, nullptr, GENERIC_ALL, nullptr, &handle), "CreateSharedHandle");
        VkSemaphoreTypeCreateInfoKHR type{.sType = VK_STRUCTURE_TYPE_SEMAPHORE_TYPE_CREATE_INFO_KHR};
        type.semaphoreType = VK_SEMAPHORE_TYPE_TIMELINE_KHR;
        VkSemaphoreCreateInfo info{.sType = VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO, .pNext = &type};
        VkSemaphore semaphore = VK_NULL_HANDLE;
        check(vkCreateSemaphore(device.handle, &info, nullptr, &semaphore), "vkCreateSemaphore");
        VkImportSemaphoreWin32HandleInfoKHR import{.sType = VK_STRUCTURE_TYPE_IMPORT_SEMAPHORE_WIN32_HANDLE_INFO_KHR};
        import.semaphore = semaphore;
        import.handleType = VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_D3D12_FENCE_BIT;
        import.handle = handle;
        VkResult result = state.importSemaphoreWin32Handle(device.handle, &import);
        CloseHandle(handle);
        if (result != VK_SUCCESS) {
            vkDestroySemaphore(device.handle, semaphore, nullptr);
            check(result, "vkImportSemaphoreWin32HandleKHR");
        }
        return semaphore;
    }

    DxgiPresenter::DxgiPresenter(const Device &device, void *window, u32 initialWidth, u32 initialHeight)
        : device(device), state(std::make_unique<State>()) {
        format = VK_FORMAT_B8G8R8A8_UNORM;
        layout = VK_IMAGE_LAYOUT_GENERAL;
        blitTarget = true;
        State &s = *state;
        s.window = static_cast<HWND>(window);
        s.getMemoryWin32HandleProperties = reinterpret_cast<PFN_vkGetMemoryWin32HandlePropertiesKHR>(
            vkGetDeviceProcAddr(device.handle, "vkGetMemoryWin32HandlePropertiesKHR"));
        s.importSemaphoreWin32Handle = reinterpret_cast<PFN_vkImportSemaphoreWin32HandleKHR>(
            vkGetDeviceProcAddr(device.handle, "vkImportSemaphoreWin32HandleKHR"));
        if (!s.getMemoryWin32HandleProperties || !s.importSemaphoreWin32Handle) fail("Win32 interop entry points are missing");

        hr(CreateDXGIFactory2(0, IID_PPV_ARGS(&s.factory)), "CreateDXGIFactory2");
        LUID luid;
        std::memcpy(&luid, device.luid, sizeof(luid));
        ComPtr<IDXGIAdapter1> adapter;
        hr(s.factory->EnumAdapterByLuid(luid, IID_PPV_ARGS(&adapter)), "EnumAdapterByLuid");
        hr(D3D12CreateDevice(adapter.Get(), D3D_FEATURE_LEVEL_11_0, IID_PPV_ARGS(&s.d3d)), "D3D12CreateDevice");

        D3D12_COMMAND_QUEUE_DESC queueDesc{};
        queueDesc.Type = D3D12_COMMAND_LIST_TYPE_DIRECT;
        queueDesc.Priority = D3D12_COMMAND_QUEUE_PRIORITY_HIGH;
        hr(s.d3d->CreateCommandQueue(&queueDesc, IID_PPV_ARGS(&s.queue)), "CreateCommandQueue");

        ComPtr<IDXGIFactory5> factory5;
        if (SUCCEEDED(s.factory.As(&factory5))) {
            BOOL allow = FALSE;
            if (SUCCEEDED(factory5->CheckFeatureSupport(DXGI_FEATURE_PRESENT_ALLOW_TEARING, &allow, sizeof(allow))))
                s.tearing = allow == TRUE;
        }
        s.flags = DXGI_SWAP_CHAIN_FLAG_FRAME_LATENCY_WAITABLE_OBJECT | (s.tearing ? DXGI_SWAP_CHAIN_FLAG_ALLOW_TEARING : 0);

        hr(s.d3d->CreateFence(0, D3D12_FENCE_FLAG_SHARED, IID_PPV_ARGS(&s.renderedFence)), "CreateFence");
        hr(s.d3d->CreateFence(0, D3D12_FENCE_FLAG_SHARED, IID_PPV_ARGS(&s.copiedFence)), "CreateFence");
        s.idleEvent = CreateEventW(nullptr, FALSE, FALSE, nullptr);
        if (!s.idleEvent) fail("CreateEvent failed");
        s.rendered = importFence(device, s, s.renderedFence.Get());
        s.copied = importFence(device, s, s.copiedFence.Get());

        DXGI_SWAP_CHAIN_DESC1 desc{};
        desc.Width = std::max(initialWidth, 1u);
        desc.Height = std::max(initialHeight, 1u);
        desc.Format = DXGI_FORMAT_B8G8R8A8_UNORM;
        desc.SampleDesc.Count = 1;
        desc.BufferUsage = DXGI_USAGE_RENDER_TARGET_OUTPUT;
        desc.BufferCount = BUFFER_COUNT;
        desc.Scaling = DXGI_SCALING_STRETCH;
        desc.SwapEffect = DXGI_SWAP_EFFECT_FLIP_DISCARD;
        desc.AlphaMode = DXGI_ALPHA_MODE_IGNORE;
        desc.Flags = s.flags;
        ComPtr<IDXGISwapChain1> swapchain1;
        hr(s.factory->CreateSwapChainForHwnd(s.queue.Get(), s.window, &desc, nullptr, nullptr, &swapchain1),
           "CreateSwapChainForHwnd");
        hr(swapchain1.As(&s.swapchain), "IDXGISwapChain3");
        s.factory->MakeWindowAssociation(s.window, DXGI_MWA_NO_ALT_ENTER | DXGI_MWA_NO_WINDOW_CHANGES);
        hr(s.swapchain->SetMaximumFrameLatency(2), "SetMaximumFrameLatency");
        s.waitable = s.swapchain->GetFrameLatencyWaitableObject();

        create(desc.Width, desc.Height, true);
    }

    DxgiPresenter::~DxgiPresenter() {
        State &s = *state;
        if (s.queue) waitIdle();
        destroyBuffers();
        if (s.rendered) vkDestroySemaphore(device.handle, s.rendered, nullptr);
        if (s.copied) vkDestroySemaphore(device.handle, s.copied, nullptr);
        if (s.idleEvent) CloseHandle(s.idleEvent);
        if (s.waitable) CloseHandle(s.waitable);
    }

    void DxgiPresenter::waitIdle() {
        State &s = *state;
        u64 value = ++s.copiedValue;
        if (FAILED(s.queue->Signal(s.copiedFence.Get(), value))) return;
        if (s.copiedFence->GetCompletedValue() < value) {
            s.copiedFence->SetEventOnCompletion(value, s.idleEvent);
            WaitForSingleObject(s.idleEvent, 5000);
        }
    }

    void DxgiPresenter::destroyBuffers() {
        State &s = *state;
        for (VkImageView view: views) vkDestroyImageView(device.handle, view, nullptr);
        for (VkImage image: images) vkDestroyImage(device.handle, image, nullptr);
        for (State::Buffer &buffer: s.buffers) {
            if (buffer.memory) vkFreeMemory(device.handle, buffer.memory, nullptr);
        }
        views.clear();
        images.clear();
        s.buffers.clear();
    }

    bool DxgiPresenter::create(u32 requestedWidth, u32 requestedHeight, bool vsync) {
        State &s = *state;
        s.vsync = vsync;
        if (requestedWidth == 0 || requestedHeight == 0) return false;
        waitIdle();
        destroyBuffers();
        hr(s.swapchain->ResizeBuffers(BUFFER_COUNT, requestedWidth, requestedHeight, DXGI_FORMAT_B8G8R8A8_UNORM, s.flags),
           "ResizeBuffers");
        width = requestedWidth;
        height = requestedHeight;
        s.buffers.resize(BUFFER_COUNT);
        images.resize(BUFFER_COUNT, VK_NULL_HANDLE);
        views.resize(BUFFER_COUNT, VK_NULL_HANDLE);

        for (u32 i = 0; i < BUFFER_COUNT; i++) {
            State::Buffer &buffer = s.buffers[i];
            hr(s.swapchain->GetBuffer(i, IID_PPV_ARGS(&buffer.back)), "GetBuffer");

            D3D12_HEAP_PROPERTIES heap{.Type = D3D12_HEAP_TYPE_DEFAULT};
            D3D12_RESOURCE_DESC desc{};
            desc.Dimension = D3D12_RESOURCE_DIMENSION_TEXTURE2D;
            desc.Width = width;
            desc.Height = height;
            desc.DepthOrArraySize = 1;
            desc.MipLevels = 1;
            desc.Format = DXGI_FORMAT_B8G8R8A8_UNORM;
            desc.SampleDesc.Count = 1;
            desc.Layout = D3D12_TEXTURE_LAYOUT_UNKNOWN;
            desc.Flags = D3D12_RESOURCE_FLAG_ALLOW_RENDER_TARGET;
            hr(s.d3d->CreateCommittedResource(&heap, D3D12_HEAP_FLAG_SHARED, &desc, D3D12_RESOURCE_STATE_COMMON, nullptr,
                                              IID_PPV_ARGS(&buffer.shared)), "CreateCommittedResource");

            VkExternalMemoryImageCreateInfoKHR external{.sType = VK_STRUCTURE_TYPE_EXTERNAL_MEMORY_IMAGE_CREATE_INFO_KHR};
            external.handleTypes = VK_EXTERNAL_MEMORY_HANDLE_TYPE_D3D12_RESOURCE_BIT;
            VkImageCreateInfo imageInfo{.sType = VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO, .pNext = &external};
            imageInfo.imageType = VK_IMAGE_TYPE_2D;
            imageInfo.format = format;
            imageInfo.extent = {width, height, 1};
            imageInfo.mipLevels = 1;
            imageInfo.arrayLayers = 1;
            imageInfo.samples = VK_SAMPLE_COUNT_1_BIT;
            imageInfo.tiling = VK_IMAGE_TILING_OPTIMAL;
            imageInfo.usage = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT |
                              VK_IMAGE_USAGE_TRANSFER_DST_BIT;
            imageInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
            imageInfo.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
            check(vkCreateImage(device.handle, &imageInfo, nullptr, &images[i]), "vkCreateImage");

            HANDLE handle = nullptr;
            hr(s.d3d->CreateSharedHandle(buffer.shared.Get(), nullptr, GENERIC_ALL, nullptr, &handle),
               "CreateSharedHandle");
            VkMemoryRequirements requirements;
            vkGetImageMemoryRequirements(device.handle, images[i], &requirements);
            u32 typeBits = requirements.memoryTypeBits;
            VkMemoryWin32HandlePropertiesKHR properties{.sType = VK_STRUCTURE_TYPE_MEMORY_WIN32_HANDLE_PROPERTIES_KHR};
            if (s.getMemoryWin32HandleProperties(device.handle, VK_EXTERNAL_MEMORY_HANDLE_TYPE_D3D12_RESOURCE_BIT, handle,
                                                 &properties) == VK_SUCCESS && (typeBits & properties.memoryTypeBits)) {
                typeBits &= properties.memoryTypeBits;
            }
            VkMemoryDedicatedAllocateInfoKHR dedicated{.sType = VK_STRUCTURE_TYPE_MEMORY_DEDICATED_ALLOCATE_INFO_KHR};
            dedicated.image = images[i];
            VkImportMemoryWin32HandleInfoKHR import{.sType = VK_STRUCTURE_TYPE_IMPORT_MEMORY_WIN32_HANDLE_INFO_KHR};
            import.pNext = &dedicated;
            import.handleType = VK_EXTERNAL_MEMORY_HANDLE_TYPE_D3D12_RESOURCE_BIT;
            import.handle = handle;
            VkMemoryAllocateInfo allocate{.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO, .pNext = &import};
            allocate.allocationSize = requirements.size;
            allocate.memoryTypeIndex = device.memoryType(typeBits, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
            VkResult result = vkAllocateMemory(device.handle, &allocate, nullptr, &buffer.memory);
            CloseHandle(handle);
            check(result, "vkAllocateMemory");
            check(vkBindImageMemory(device.handle, images[i], buffer.memory, 0), "vkBindImageMemory");

            VkImageViewCreateInfo viewInfo{.sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO};
            viewInfo.image = images[i];
            viewInfo.viewType = VK_IMAGE_VIEW_TYPE_2D;
            viewInfo.format = format;
            viewInfo.subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1};
            check(vkCreateImageView(device.handle, &viewInfo, nullptr, &views[i]), "vkCreateImageView");

            hr(s.d3d->CreateCommandAllocator(D3D12_COMMAND_LIST_TYPE_DIRECT, IID_PPV_ARGS(&buffer.allocator)),
               "CreateCommandAllocator");
            hr(s.d3d->CreateCommandList(0, D3D12_COMMAND_LIST_TYPE_DIRECT, buffer.allocator.Get(), nullptr,
                                        IID_PPV_ARGS(&buffer.copy)), "CreateCommandList");
            D3D12_RESOURCE_BARRIER barriers[2]{};
            barriers[0].Type = D3D12_RESOURCE_BARRIER_TYPE_TRANSITION;
            barriers[0].Transition.pResource = buffer.back.Get();
            barriers[0].Transition.Subresource = D3D12_RESOURCE_BARRIER_ALL_SUBRESOURCES;
            barriers[0].Transition.StateBefore = D3D12_RESOURCE_STATE_PRESENT;
            barriers[0].Transition.StateAfter = D3D12_RESOURCE_STATE_COPY_DEST;
            barriers[1].Type = D3D12_RESOURCE_BARRIER_TYPE_TRANSITION;
            barriers[1].Transition.pResource = buffer.shared.Get();
            barriers[1].Transition.Subresource = D3D12_RESOURCE_BARRIER_ALL_SUBRESOURCES;
            barriers[1].Transition.StateBefore = D3D12_RESOURCE_STATE_COMMON;
            barriers[1].Transition.StateAfter = D3D12_RESOURCE_STATE_COPY_SOURCE;
            buffer.copy->ResourceBarrier(2, barriers);
            buffer.copy->CopyResource(buffer.back.Get(), buffer.shared.Get());
            for (D3D12_RESOURCE_BARRIER &barrier: barriers) {
                std::swap(barrier.Transition.StateBefore, barrier.Transition.StateAfter);
            }
            buffer.copy->ResourceBarrier(2, barriers);
            hr(buffer.copy->Close(), "Close");
        }
        return true;
    }

    int DxgiPresenter::acquire(VkSemaphore) {
        return static_cast<int>(state->swapchain->GetCurrentBackBufferIndex());
    }

    void DxgiPresenter::sync(SubmitSync &sync, VkCommandBuffer commands, u32 index, bool first, bool presenting) {
        State &s = *state;
        sync.timeline = true;
        u64 copiedAt = s.buffers[index].copiedAt;
        if (first && s.copiedFence->GetCompletedValue() < copiedAt) {
            sync.wait(s.copied, VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT | VK_PIPELINE_STAGE_TRANSFER_BIT,
                      copiedAt);
        }
        if (presenting) {
            VkImageMemoryBarrier barrier{.sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER};
            barrier.srcAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT | VK_ACCESS_TRANSFER_READ_BIT |
                                    VK_ACCESS_TRANSFER_WRITE_BIT;
            barrier.oldLayout = VK_IMAGE_LAYOUT_GENERAL;
            barrier.newLayout = VK_IMAGE_LAYOUT_GENERAL;
            barrier.srcQueueFamilyIndex = device.queueFamily;
            barrier.dstQueueFamilyIndex = VK_QUEUE_FAMILY_EXTERNAL_KHR;
            barrier.image = images[index];
            barrier.subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1};
            vkCmdPipelineBarrier(commands, VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT | VK_PIPELINE_STAGE_TRANSFER_BIT,
                                 VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, 0, 0, nullptr, 0, nullptr, 1, &barrier);
            sync.signal(s.rendered, ++s.renderedValue);
        }
    }

    bool DxgiPresenter::present(u32 index) {
        State &s = *state;
        State::Buffer &buffer = s.buffers[index];
        hr(s.queue->Wait(s.renderedFence.Get(), s.renderedValue), "ID3D12CommandQueue::Wait");
        ID3D12CommandList *lists[] = {buffer.copy.Get()};
        s.queue->ExecuteCommandLists(1, lists);
        hr(s.queue->Signal(s.copiedFence.Get(), ++s.copiedValue), "ID3D12CommandQueue::Signal");
        buffer.copiedAt = s.copiedValue;
        UINT flags = !s.vsync && s.tearing ? DXGI_PRESENT_ALLOW_TEARING : 0;
        HRESULT result = s.swapchain->Present(s.vsync ? 1 : 0, flags);
        if (result == DXGI_ERROR_DEVICE_REMOVED || result == DXGI_ERROR_DEVICE_RESET) hr(result, "Present");
        if (FAILED(result) && flags) {
            log::warn("Present with tearing failed with HRESULT 0x%08lX, retrying without it", static_cast<unsigned long>(result));
            s.tearing = false;
            result = s.swapchain->Present(0, 0);
        }
        if (FAILED(result)) log::warn("Present failed with HRESULT 0x%08lX", static_cast<unsigned long>(result));
        if (s.waitable) WaitForSingleObjectEx(s.waitable, 1000, TRUE);
        return SUCCEEDED(result);
    }

    bool DxgiPresenter::setVsync(bool enabled) {
        state->vsync = enabled;
        return false;
    }
} // namespace at

#endif
