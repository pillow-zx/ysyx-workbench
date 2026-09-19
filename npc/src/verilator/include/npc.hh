#ifndef NPC_NPC_HH
#define NPC_NPC_HH

#include <cpu.hh>
#include <options.hh>

enum class NpcStatus {
    Running,
    Exit,
};

class Npc {
public:
    Npc(int argc, char *argv[], const NpcOptions &options);
    ~Npc();

    auto run() -> void;

private:
    auto executeCpu() -> void;
    auto executeCpu(std::size_t cycles) -> void;
    auto showRegisters() const -> void;

    Cpu cpu_;
    NpcStatus status_ = NpcStatus::Running;
    bool batchMode_ = config::args::batch;
};

#endif // NPC_NPC_HH
