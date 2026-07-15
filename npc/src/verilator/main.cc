#include <npc.hh>

auto main(const int argc, char *argv[]) -> int {
    Npc npc(argc, argv);
    npc.run();
    return 0;
}
