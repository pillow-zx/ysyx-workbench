#ifndef NPC_ADDRESS_SPACE_HH
#define NPC_ADDRESS_SPACE_HH

#include <cstdint>

#include <mmio.hh>

class DpicBridge {
public:
    explicit DpicBridge(Mmio &mmio) : mmio_(mmio) {}

    [[nodiscard]] static auto fetchInst(std::uint32_t addr) -> std::uint32_t;

    [[nodiscard]] auto readData(std::uint32_t addr) const -> std::uint32_t;

    auto writeData(std::uint32_t addr, std::uint32_t data, std::uint8_t wmask) const -> void;

private:
    Mmio &mmio_;
};

#endif // NPC_ADDRESS_SPACE_HH
