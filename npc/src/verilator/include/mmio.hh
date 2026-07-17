#ifndef NPC_MMIO_HH
#define NPC_MMIO_HH

#include <algorithm>
#include <cstdint>
#include <device.hh>
#include <functional>
#include <optional>
#include <string>
#include <vector>

class Mmio {
public:
    auto map(const std::string &name, std::uint64_t addr, std::size_t len,
             std::reference_wrapper<Device> device) -> void;

    auto isMapped(std::uint64_t addr) const -> bool;

    auto read(std::uint64_t addr, std::size_t len) const -> std::uint64_t;

    auto write(std::uint64_t addr, std::size_t len, std::uint64_t data) const -> void;

private:
    struct Mapping {
        std::string name;
        std::uint64_t base;
        std::uint64_t len;
        std::reference_wrapper<Device> device;
    };

    std::vector<Mapping> mapping_;

    [[nodiscard]] auto findDevice(std::uint64_t addr) const -> std::optional<std::reference_wrapper<const Mapping>> {
        const auto mapping = std::ranges::find_if(mapping_, [addr](const auto &entry) {
            return addr >= entry.base && addr < entry.base + entry.len;
        });

        if (mapping == mapping_.end()) {
            return std::nullopt;
        }

        return std::cref(*mapping);
    }
};

#endif // NPC_MMIO_HH
