#include <mmio.hh>

#include <stdexcept>

auto Mmio::map(const std::string_view name, const std::uint64_t addr,
               const std::size_t len, const std::reference_wrapper<Device> device) -> void {
    mapping_.push_back({std::string{name}, addr, len, device});
}

auto Mmio::isMapped(const std::uint64_t addr) const -> bool {
    return findDevice(addr).has_value();
}

auto Mmio::read(const std::uint64_t addr, const std::size_t len) const -> std::uint64_t {
    const auto map = findDevice(addr);
    if (!map.has_value()) {
        throw std::runtime_error("MMIO address is out of bound");
    }

    return map->get().device.get().read(addr - map->get().base, len);
}

auto Mmio::write(const std::uint64_t addr, const std::size_t len, const std::uint64_t data) const -> void {
    const auto map = findDevice(addr);
    if (!map.has_value()) {
        throw std::runtime_error("MMIO address is out of bound");
    }

    map->get().device.get().write(addr - map->get().base, len, data);
}
