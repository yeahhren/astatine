#pragma once

#include "core/common.h"
#include "terrain/occlusion.h"

#include <condition_variable>
#include <memory>
#include <mutex>
#include <thread>
#include <vector>

namespace at::terrain {
    struct GraphNode {
        Visibility visibility = 0;
        i32 x = 0;
        i32 z = 0;
        u8 y = 0;
        u8 state = 0;
    };

    namespace graph {
        constexpr u8 VALID = 1;
        constexpr u8 COMPILED = 2;
    }

    struct CullInput {
        std::vector<GraphNode> nodes;
        i32 size = 0;
        i32 viewDistance = 0;
        i32 originX = 0, originY = 0, originZ = 0;
        bool smartCull = true;
        u64 graphVersion = 0;
    };

    struct CullResult {
        i32 originX = 0, originY = 0, originZ = 0;
        u64 graphVersion = 0;
        std::vector<u32> regular;
        std::vector<u32> wide;
        std::vector<u8> member;
    };

    class Culler {
    public:
        Culler();

        ~Culler();

        Culler(const Culler &) = delete;

        Culler &operator=(const Culler &) = delete;

        bool busy() const;

        void submit(std::unique_ptr<CullInput> input);

        std::unique_ptr<CullResult> take(bool wait);

        void cancel();

        void recycle(std::unique_ptr<CullResult> result);

    private:
        void run();

        void cull(const CullInput &input, CullResult &result);

        mutable std::mutex lock;
        std::condition_variable wake;
        std::condition_variable finished;
        std::unique_ptr<CullInput> pending;
        std::unique_ptr<CullResult> done;
        std::unique_ptr<CullResult> spare;
        bool running = false;
        bool stopping = false;
        u64 generation = 0;
        std::vector<u32> visited;
        std::vector<u8> incomingWide;
        std::vector<u8> incomingRegular;
        std::vector<u32> current;
        std::vector<u32> next;
        u32 token = 0;
        std::thread worker;
    };
} // namespace at::terrain
