#ifndef NPC_MM_HH
#define NPC_MM_HH

#include <array>
#include <cstdint>
#include <string>
#include <vector>

class Memory {
public:
    [[nodiscard]] static auto getBaseAddress() -> std::size_t;

    [[nodiscard]] static auto init(const std::string &filename) -> bool;

    [[nodiscard]] static auto getMemory() -> std::vector<uint8_t> &;

    [[nodiscard]] static auto fetchInst(std::uint32_t addr) -> std::uint32_t;

    [[nodiscard]] static auto readData(std::uint32_t addr, std::uint32_t size) -> std::uint32_t;

    static auto writeData(std::uint32_t addr, std::uint32_t data, std::uint32_t wmask) -> void;

    static auto showMemory(std::uint32_t addr, std::uint32_t len) -> void;

private:
    static constexpr std::array<std::uint32_t, 5> img = {
        0x00000297, // auipc t0,0
        0x00028823, // sb  zero,16(t0)
        0x0102c503, // lbu a0,16(t0)
        0x00100073, // ebreak (used as nemu_trap)
        0xdeadbeef, // some data
    };

    static constexpr std::size_t MEMORYSTART = 0x80000000;
    static constexpr std::size_t MEMORYSIZE  = 0x8000000;

    inline static auto memory = std::vector<std::uint8_t>(MEMORYSIZE, 0);

    [[nodiscard]] static auto loadProgram(const std::string &filename = "") -> bool;
};

inline auto Memory::getMemory() -> std::vector<uint8_t> & {
    return memory;
}

#endif //NPC_MM_HH
