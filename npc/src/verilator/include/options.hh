#ifndef NPC_OPTIONS_HH
#define NPC_OPTIONS_HH

#include <optional>
#include <string>

#include "generated/autoconfig.hh"

struct NpcOptions {
    std::string image;
    std::optional<std::string> log;
    std::optional<std::string> elf;
    bool batch = config::args::batch;
};

auto parseOptions(int argc, char *argv[]) -> NpcOptions;

#endif // NPC_OPTIONS_HH
