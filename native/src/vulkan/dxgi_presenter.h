#pragma once

#ifdef _WIN32

#include "core/common.h"
#include "core/vk.h"
#include "vulkan/presenter.h"

#include <memory>

namespace at {
    class Device;

    class DxgiPresenter final : public Presenter {
    public:
        DxgiPresenter(const Device &device, void *window, u32 initialWidth, u32 initialHeight);

        ~DxgiPresenter() override;

        bool create(u32 requestedWidth, u32 requestedHeight, bool vsync) override;

        int acquire(VkSemaphore available) override;

        void sync(SubmitSync &sync, VkCommandBuffer commands, u32 index, bool first, bool presenting) override;

        bool present(u32 index) override;

        bool setVsync(bool enabled) override;

        struct State;

    private:

        void destroyBuffers();

        void waitIdle();

        const Device &device;
        std::unique_ptr<State> state;
    };
} // namespace at

#endif
