#include <iomanip>
#include <iostream>
#include <string>

#include <mm.hh>
#include <monitor.hh>
#include <npc.hh>

Npc::Npc(const int argc, char *argv[], const NpcOptions &options)
    : cpu(argc, argv, options.log, options.elf), batchMode(options.batch) {
    if (!Memory::init(options.image)) {
        status = NpcStatus::Exit;
        return;
    }
    cpu.reset();

    cpu.initDifftest(Memory::getBaseAddress(), Memory::getMemory());
}

auto Npc::executeCpu() -> void {
    if (cpu.exec()) {
        status = NpcStatus::Exit;
    }
}

auto Npc::executeCpu(const std::size_t cycles) -> void {
    if (cpu.exec(cycles)) {
        status = NpcStatus::Exit;
    }
}

auto Npc::showRegisters() const -> void {
    const auto flags = std::cout.flags();
    const auto fill = std::cout.fill();

    for (std::size_t index = 0; index < registerNames.size(); ++index) {
        std::cout << std::left << std::setw(4) << registerNames[index] << " 0x" << std::right
                << std::hex << std::setw(8) << std::setfill('0') << cpu.getReg(index)
                << std::setfill(' ') << ((index % 4 == 3) ? '\n' : ' ');
    }

    std::cout.flags(flags);
    std::cout.fill(fill);
}

auto Npc::run() -> void {
    if (status == NpcStatus::Exit) {
        return;
    }

    if (batchMode) {
        std::cout << "Running in batch mode..." << std::endl;
        executeCpu();
        return;
    }

    while (status != NpcStatus::Exit) {
        const auto commands = getCommands("(npc) > ");
        if (commands.empty()) {
            continue;
        }

        try {
            if (commands[0] == "q") {
                status = NpcStatus::Exit;
            } else if (commands[0] == "c") {
                executeCpu();
            } else if (commands[0] == "si") {
                const auto cycles = commands.size() > 1 ? std::stoull(commands[1]) : 1;
                executeCpu(cycles);
            } else if (commands[0] == "info" && commands.size() > 1 && commands[1] == "r") {
                showRegisters();
            } else if (commands[0] == "x" && commands.size() > 2) {
                const auto len = std::stoull(commands[1]);
                const auto addr = std::stoull(commands[2], nullptr, 0);
                Memory::showMemory(static_cast<std::uint32_t>(addr), static_cast<std::uint32_t>(len));
            } else {
                std::cout << "Unknown command." << std::endl;
            }
        } catch (const std::exception &error) {
            std::cout << "Invalid command argument: " << error.what() << std::endl;
        }
    }
}
