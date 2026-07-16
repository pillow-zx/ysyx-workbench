#ifndef NPC_OPTIONS_HH
#define NPC_OPTIONS_HH

#include <optional>
#include <string>

struct NpcOptions {
    std::string image;
    std::optional<std::string> log;
    std::optional<std::string> elf;
    bool batch = false;
};

auto parseOptions(int argc, char *argv[]) -> NpcOptions;

#endif // NPC_OPTIONS_HH
