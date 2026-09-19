#include <npc.hh>
#include <verilated.h>

auto main(const int argc, char *argv[]) -> int {
    Verilated::commandArgs(argc, argv);
    const auto options = parseOptions(argc, argv);
    Npc npc(argc, argv, options);
    npc.run();
    return 0;
}
