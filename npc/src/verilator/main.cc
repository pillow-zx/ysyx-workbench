#include <npc.hh>

auto main(const int argc, char *argv[]) -> int {
    const auto options = parseOptions(argc, argv);
    Npc npc(argc, argv, options);
    npc.run();
    return 0;
}
