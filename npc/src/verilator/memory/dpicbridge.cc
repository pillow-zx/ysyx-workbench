#include <dpicbridge.hh>

#include <mm.hh>

auto DpicBridge::fetchInst(const std::uint32_t addr) -> std::uint32_t {
    return Memory::fetchInst(addr);
}

auto DpicBridge::readData(const std::uint32_t addr) const -> std::uint32_t {
    if (mmio_.isMapped(addr)) {
        return static_cast<std::uint32_t>(mmio_.read(addr, sizeof(std::uint32_t)));
    }

    return Memory::readData(addr, sizeof(std::uint32_t));
}

auto DpicBridge::writeData(const std::uint32_t addr, const std::uint32_t data,
                             const std::uint8_t wmask) const -> void {
    for (std::uint32_t lane = 0; lane < sizeof(std::uint32_t); ++lane) {
        const auto laneMask = static_cast<std::uint8_t>(1U << lane);
        if ((wmask & laneMask) == 0) {
            continue;
        }

        if (const auto laneAddress = addr + lane; mmio_.isMapped(laneAddress)) {
            mmio_.write(laneAddress, 1, data >> (lane * 8) & 0xffU);
        } else {
            Memory::writeData(addr, data, laneMask);
        }
    }
}
