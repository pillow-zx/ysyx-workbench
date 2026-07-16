#ifndef NPC_NPC_HH
#define NPC_NPC_HH

#include <cpu.hh>
#include <options.hh>

#include <generated/autoconfig.hh>

enum class NpcStatus {
    Running,
    Exit,
};

class Npc {
public:
    Npc(int argc, char *argv[], const NpcOptions &options);

    auto run() -> void;

private:
    auto executeCpu() -> void;
    auto executeCpu(std::size_t cycles) -> void;
    auto showRegisters() const -> void;

    Cpu cpu;
    NpcStatus status = NpcStatus::Running;
    bool batchMode = config::args::batch;
};

#endif // NPC_NPC_HH
