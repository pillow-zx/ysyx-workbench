#ifndef NPC_CPU_HH
#define NPC_CPU_HH

#include <array>
#include <cstddef>
#include <cstdint>
#include <memory>
#include <optional>
#include <string_view>

static inline constexpr std::array<std::string_view, 32> registerNames = {
    "$0", "ra", "sp", "gp", "tp", "t0", "t1", "t2", "s0", "s1", "a0",
    "a1", "a2", "a3", "a4", "a5", "a6", "a7", "s2", "s3", "s4", "s5",
    "s6", "s7", "s8", "s9", "s10", "s11", "t3", "t4", "t5", "t6",
};

class Cpu {
public:
    Cpu(int argc, char *argv[]);
    ~Cpu();

    Cpu(const Cpu &) = delete;
    auto operator=(const Cpu &) -> Cpu & = delete;
    Cpu(Cpu &&) = delete;
    auto operator=(Cpu &&) -> Cpu & = delete;

    auto reset() const -> void;

    [[nodiscard]] auto exec(std::optional<std::size_t> cycles = std::nullopt) const -> bool;

    [[nodiscard]] auto getReg(std::size_t index) const -> std::uint32_t;

    [[nodiscard]] auto getPc() const -> std::uint32_t;

private:
    class Impl;
    std::unique_ptr<Impl> impl;
};

#endif // NPC_CPU_HH
