#ifndef NPC_CPU_HH
#define NPC_CPU_HH


#include <array>
#include <cstddef>
#include <vector>
#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <string_view>

static inline constexpr std::array<std::string_view, 32> registerNames = {
    "$0", "ra", "sp", "gp", "tp", "t0", "t1", "t2", "s0", "s1", "a0",
    "a1", "a2", "a3", "a4", "a5", "a6", "a7", "s2", "s3", "s4", "s5",
    "s6", "s7", "s8", "s9", "s10", "s11", "t3", "t4", "t5", "t6",
};

class Cpu {
public:
    Cpu(int argc, char *argv[], const std::optional<std::string> &itracePath, const std::optional<std::string> &elfPath);
    ~Cpu();

    Cpu(const Cpu &) = delete;
    auto operator=(const Cpu &) -> Cpu & = delete;
    Cpu(Cpu &&) = delete;
    auto operator=(Cpu &&) -> Cpu & = delete;

    auto reset() const -> void;

    auto initDifftest(std::uint32_t memoryBase, const std::vector<std::uint8_t> &memory) const -> void;

    [[nodiscard]] auto exec(std::optional<std::size_t> cycles = std::nullopt) const -> bool;

    [[nodiscard]] auto getReg(std::size_t index) const -> std::uint32_t;

    [[nodiscard]] auto getAllRegs() const -> std::vector<std::uint32_t>;

    [[nodiscard]] auto getPc() const -> std::uint32_t;

private:
    class Impl;
    std::unique_ptr<Impl> impl;

    class Trace;
    std::unique_ptr<Trace> trace;

    class DiffTest;
    std::unique_ptr<DiffTest> diff;

    constexpr static std::uint32_t REGNUM = 32;
};

#endif // NPC_CPU_HH
