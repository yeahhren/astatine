#pragma once

#include "core/common.h"

#include <algorithm>
#include <vector>

namespace at {
    class U64Map {
    public:
        explicit U64Map(const std::size_t capacity = 64) : keys(capacity, EMPTY), values(capacity, 0) {
        }

        [[nodiscard]] AT_INLINE u64 get(const u64 key) const {
            const std::size_t mask = keys.size() - 1;
            std::size_t index = mix(key) & mask;
            while (true) {
                const u64 current = keys[index];
                if (current == key) return values[index];
                if (current == EMPTY) return 0;
                index = (index + 1) & mask;
            }
        }

        void put(const u64 key, const u64 value) {
            if ((count + 1) * 2 > keys.size()) grow();
            insert(key, value);
        }

        template<typename F>
        void forEach(F &&action) const {
            for (std::size_t i = 0; i < keys.size(); i++) {
                if (keys[i] != EMPTY) action(keys[i], values[i]);
            }
        }

        void clear() {
            std::ranges::fill(keys, EMPTY);
            count = 0;
        }

    private:
        static constexpr u64 EMPTY = ~0ull;

        static AT_INLINE std::size_t mix(const u64 key) {
            const u64 hash = key * 0x9E3779B97F4A7C15ull;
            return hash ^ (hash >> 32);
        }

        void insert(const u64 key, const u64 value) {
            const std::size_t mask = keys.size() - 1;
            std::size_t index = mix(key) & mask;
            while (keys[index] != EMPTY && keys[index] != key) index = (index + 1) & mask;
            if (keys[index] == EMPTY) count++;
            keys[index] = key;
            values[index] = value;
        }

        void grow() {
            const std::vector<u64> oldKeys = std::move(keys);
            const std::vector<u64> oldValues = std::move(values);
            keys.assign(oldKeys.size() * 2, EMPTY);
            values.assign(oldKeys.size() * 2, 0);
            count = 0;
            for (std::size_t i = 0; i < oldKeys.size(); i++) {
                if (oldKeys[i] != EMPTY) insert(oldKeys[i], oldValues[i]);
            }
        }

        std::vector<u64> keys;
        std::vector<u64> values;
        std::size_t count = 0;
    };
} // namespace at
