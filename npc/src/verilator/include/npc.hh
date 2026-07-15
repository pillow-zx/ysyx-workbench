#ifndef NPC_NPC_HH
#define NPC_NPC_HH

#include <cpu.hh>

enum class NpcStatus {
    Running,
    Stop,
    Exit,
};

class Npc {
public:
    Npc(int argc, char *argv[]);

    auto run() -> void;

private:
    auto executeCpu() -> void;
    auto executeCpu(std::size_t cycles) -> void;
    auto showRegisters() const -> void;

    Cpu cpu;
    NpcStatus status = NpcStatus::Running;
    bool batchMode = false;
};

#endif // NPC_NPC_HH
