#ifndef NPC_NPC_HH
#define NPC_NPC_HH

#include <dpicbridge.hh>
#include <cpu.hh>
#include <mmio.hh>
#include <options.hh>

#include <generated/autoconfig.hh>


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

    static constexpr std::uint64_t SERIALBASE = 0xa00003f8;
    static constexpr std::uint64_t RTCBASE = 0xa0000048;

    SerialDevice serial_;
    RtcDevice rtc_;
    Mmio mmio_;
    DpicBridge bridge{mmio_};
    Cpu cpu_;
    NpcStatus status_ = NpcStatus::Running;
    bool batchMode_ = config::args::batch;
    bool dpiBound_ = false;
};

#endif // NPC_NPC_HH
