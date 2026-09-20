#ifndef NPC_MONITOR_HH
#define NPC_MONITOR_HH

#include <cstddef>
#include <cstdint>
#include <expected>
#include <functional>
#include <optional>
#include <string>
#include <string_view>
#include <vector>

namespace monitor {

struct ExprError {
    std::size_t position = 0;
    std::string message;
};

struct ExprContext {
    std::function<std::optional<std::uint32_t>(std::string_view name)> readRegister;
    std::function<std::uint32_t(std::uint32_t addres, std::size_t size)> readMemory;
};

[[nodiscard]] auto expr(std::string_view expression, const ExprContext &context)
    -> std::expected<std::uint32_t, ExprError>;

} // namespace monitor

[[nodiscard]] auto getCommands(std::string_view prompt) -> std::vector<std::string>;

[[nodiscard]] auto joinArguments(const std::vector<std::string> &arguments, const std::size_t first) -> std::string;

#endif // NPC_MONITOR_HH
